package com.zyren.app.plan

import com.zyren.app.store.JsonStore
import kotlinx.serialization.json.*
import java.util.UUID

enum class TaskState(val label: String) { BEKLIYOR("Bekliyor"), DEVAM("Devam ediyor"), BITTI("Bitti"), BASARISIZ("Başarısız"), ERTELENDI("Ertelendi") }

class Task(val id: String, var title: String, var priority: Int, var due: Long?, var state: TaskState, var tries: Int, var note: String)
class Goal(val id: String, val title: String, val horizon: String, val createdAt: Long, val tasks: MutableList<Task>)

/** Hedef → görev bölme kaydı, öncelik, ilerleme, gecikeni/başarısızı yeniden planlama. "Bitti" kanıt ister. */
class PlanEngine(private val store: JsonStore? = null, private val idGen: () -> String = { UUID.randomUUID().toString().take(8) }) {
    companion object { const val DAY = 86_400_000L; const val MAX_TRIES = 3 }
    val goals = mutableListOf<Goal>()

    init { reload() }

    fun addGoal(title: String, horizon: String, items: List<Triple<String, Int, Int?>>, now: Long): Goal {
        require(title.isNotBlank()) { "Hedef başlığı boş" }
        require(items.isNotEmpty()) { "En az bir görev gerekli" }
        val tasks = items.take(30).map { (t, p, days) -> Task(idGen(), t.take(200), p.coerceIn(1, 10), days?.let { now + it * DAY }, TaskState.BEKLIYOR, 0, "") }
        val g = Goal(idGen(), title.take(200), horizon, now, tasks.toMutableList())
        goals += g; save(); return g
    }

    fun find(taskId: String): Pair<Goal, Task>? = goals.firstNotNullOfOrNull { g -> g.tasks.firstOrNull { it.id == taskId }?.let { g to it } }

    /** Hata mesajı veya null. BİTTİ için kanıt (test sonucu/kullanıcı onayı) zorunlu. */
    fun setState(taskId: String, state: TaskState, note: String, evidence: String?): String? {
        val (_, t) = find(taskId) ?: return "Görev bulunamadı: $taskId"
        if (state == TaskState.BITTI && evidence.isNullOrBlank()) return "Bir görev kanıtsız 'Bitti' yapılamaz (test sonucu veya kullanıcı onayı gerekir)"
        t.state = state
        t.note = (note + if (state == TaskState.BITTI) " [kanıt: $evidence]" else "").trim().take(300)
        save(); return null
    }

    fun progress(g: Goal): Pair<Int, Int> = g.tasks.count { it.state == TaskState.BITTI } to g.tasks.size

    fun overdue(now: Long): List<Task> = goals.flatMap { it.tasks }.filter { it.state != TaskState.BITTI && it.state != TaskState.ERTELENDI && (it.state == TaskState.BASARISIZ || (it.due != null && it.due!! < now)) }

    /** Geciken/başarısız görevi yeniden planlar (artan bekleme + öncelik artışı); 3 denemeden sonra ertelenir. */
    fun reschedule(now: Long): List<String> {
        val lines = mutableListOf<String>()
        for (t in overdue(now)) {
            t.tries++
            if (t.tries >= MAX_TRIES) { t.state = TaskState.ERTELENDI; t.note = "3 denemede tamamlanamadı; yeniden ele alınmalı"; lines += "ERTELENDİ: ${t.title}" }
            else { t.state = TaskState.BEKLIYOR; t.due = now + DAY * t.tries; t.priority = (t.priority + 1).coerceAtMost(10); lines += "YENİDEN PLANLANDI (${t.tries}. deneme): ${t.title}" }
        }
        if (lines.isNotEmpty()) save()
        return lines
    }

    fun nextUp(now: Long, n: Int = 5): List<Pair<Goal, Task>> =
        goals.flatMap { g -> g.tasks.filter { it.state == TaskState.BEKLIYOR || it.state == TaskState.DEVAM }.map { g to it } }
            .sortedWith(compareByDescending<Pair<Goal, Task>> { it.second.priority }.thenBy { it.second.due ?: Long.MAX_VALUE }).take(n)

    fun render(): String = if (goals.isEmpty()) "Plan yok." else goals.joinToString("\n\n") { g ->
        val (d, t) = progress(g)
        "• ${g.title} [${g.horizon}] $d/$t\n" + g.tasks.joinToString("\n") { "   - (${it.id}) ${it.title} · öncelik ${it.priority} · ${it.state.label}" + if (it.note.isNotBlank()) " · ${it.note}" else "" }
    }

    private fun save() {
        store?.write(buildJsonArray {
            goals.forEach { g -> add(buildJsonObject {
                put("id", g.id); put("title", g.title); put("horizon", g.horizon); put("createdAt", g.createdAt)
                put("tasks", buildJsonArray { g.tasks.forEach { t -> add(buildJsonObject {
                    put("id", t.id); put("title", t.title); put("priority", t.priority); put("due", t.due ?: -1L)
                    put("state", t.state.name); put("tries", t.tries); put("note", t.note) }) } })
            }) }
        })
    }

    fun reload() {
        goals.clear()
        store?.read()?.forEach { e ->
            runCatching {
                val o = e.jsonObject
                goals += Goal(o["id"]!!.jsonPrimitive.content, o["title"]!!.jsonPrimitive.content, o["horizon"]!!.jsonPrimitive.content,
                    o["createdAt"]!!.jsonPrimitive.long,
                    o["tasks"]!!.jsonArray.map { x -> val t = x.jsonObject
                        Task(t["id"]!!.jsonPrimitive.content, t["title"]!!.jsonPrimitive.content, t["priority"]!!.jsonPrimitive.int,
                            t["due"]!!.jsonPrimitive.long.let { d -> if (d < 0) null else d }, TaskState.valueOf(t["state"]!!.jsonPrimitive.content),
                            t["tries"]!!.jsonPrimitive.int, t["note"]!!.jsonPrimitive.content) }.toMutableList())
            }
        }
    }
}
