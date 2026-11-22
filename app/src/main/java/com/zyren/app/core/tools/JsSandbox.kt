package com.zyren.app.core.tools

import org.mozilla.javascript.ClassShutter
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.RhinoException
import org.mozilla.javascript.Undefined

/**
 * JavaScript kum havuzu (Rhino, yorumlayıcı modu). Ana iş parçacığını DONDURMAZ: ayrı iş parçacığında çalışır.
 * Sınırlar: süre (talimat sayacıyla kesilir), yığın derinliği, bellek artışı, çıktı boyutu.
 * Java sınıflarına erişim yoktur (initSafeStandardObjects + ClassShutter); dosya/ağ/telefon API'si yoktur.
 */
class JsSandbox(private val timeoutMs: Long = 3000, private val maxHeapGrowth: Long = 96L * 1024 * 1024) {
    class Outcome(val output: String, val value: String, val error: String?) {
        fun text(): String = if (error != null) (if (output.isNotEmpty()) output + "\n" else "") + error
        else (if (output.isNotEmpty()) output + "\n" else "") + "sonuç: " + value
    }

    private class JsLimit(msg: String) : Error(msg) // java.lang.Error: betikteki try/catch yakalayamaz

    private class Factory(val deadline: Long, val baseHeap: Long, val maxGrowth: Long) : ContextFactory() {
        override fun makeContext(): Context {
            val cx = super.makeContext()
            cx.setOptimizationLevel(-1)
            cx.setInstructionObserverThreshold(2000)
            cx.setMaximumInterpreterStackDepth(200)
            cx.setLanguageVersion(Context.VERSION_ES6)
            cx.setClassShutter(ClassShutter { false })
            return cx
        }
        override fun observeInstructionCount(cx: Context, instructionCount: Int) {
            if (System.nanoTime() > deadline) throw JsLimit("zaman aşımı")
            val rt = Runtime.getRuntime()
            if (rt.totalMemory() - rt.freeMemory() - baseHeap > maxGrowth) throw JsLimit("bellek sınırı aşıldı")
        }
    }

    private val prelude = "var __out=[];var __n=0;var console={log:function(){var s=Array.prototype.map.call(arguments,function(a){" +
        "try{return typeof a==='object'?JSON.stringify(a):String(a)}catch(e){return String(a)}}).join(' ');" +
        "if(__n<20000){__out.push(s);__n+=s.length}}};console.info=console.log;console.warn=console.log;console.error=console.log;"

    @Volatile private var stuck = false

    fun evaluate(code: String): Outcome {
        if (stuck) return Outcome("", "", "HATA: önceki betik durdurulamadı; uygulamayı yeniden başlatın")
        if (code.length > 20_000) return Outcome("", "", "HATA: kod 20.000 karakterden uzun")
        val box = arrayOfNulls<Outcome>(1)
        val rt = Runtime.getRuntime()
        val base = rt.totalMemory() - rt.freeMemory()
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        val t = Thread(null, {
            box[0] = try { eval(code, Factory(deadline, base, maxHeapGrowth)) }
            catch (e: JsLimit) { Outcome("", "", "HATA: ${e.message}") }
            catch (e: OutOfMemoryError) { Outcome("", "", "HATA: bellek sınırı aşıldı") }
            catch (e: StackOverflowError) { Outcome("", "", "HATA: yığın taştı") }
            catch (e: Throwable) { Outcome("", "", "HATA: ${e.message ?: e.javaClass.simpleName}") }
        }, "zyren-js", 1024L * 1024)
        t.isDaemon = true
        t.start()
        t.join(timeoutMs + 2000)
        if (t.isAlive) { stuck = true; return Outcome("", "", "HATA: betik durdurulamadı (motor takıldı)") }
        return box[0] ?: Outcome("", "", "HATA: sonuç alınamadı")
    }

    fun run(code: String): String = evaluate(code).text()

    /** Kullanıcı tanımlı saf-JS araç: `function run(args){...}` tanımlar; argümanlar JSON olarak verilir. */
    fun call(code: String, argsJson: String): Outcome = evaluate("$code\n;JSON.stringify(run($argsJson))")

    private fun eval(code: String, factory: Factory): Outcome {
        val cx = factory.enterContext()
        try {
            val scope = cx.initSafeStandardObjects()
            cx.evaluateString(scope, prelude, "prelude", 1, null)
            var err: String? = null
            var value = "undefined"
            try {
                val v = cx.evaluateString(scope, code, "kod", 1, null)
                value = if (v === Undefined.instance) "undefined" else Context.toString(v)
            } catch (e: RhinoException) {
                err = "HATA: ${e.message}"
            }
            val out = Context.toString(cx.evaluateString(scope, "__out.join('\\n')", "out", 1, null))
            return Outcome(out, value, err)
        } finally {
            Context.exit()
        }
    }
}
