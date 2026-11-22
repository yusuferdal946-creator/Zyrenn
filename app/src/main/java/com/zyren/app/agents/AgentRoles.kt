package com.zyren.app.agents

/** Uzman ajan rolleri: her rol yalnızca kendi eklentilerinin araçlarını görür; izinler ortak PermissionManager'dan geçer. */
class Role(val id: String, val name: String, val plugins: Set<String>, val prompt: String)

object AgentRoles {
    val all = listOf(
        Role("kod", "Kod ajanı", setOf("files", "code", "memory", "knowledge", "quality"), "Sen kıdemli bir yazılım geliştiricisin. Kod yaz, güvenli terminal/JS ile test et, hataları düzelt, başarısızlıkları kaydet. Testsiz 'bitti' deme."),
        Role("arastirma", "Araştırma ajanı", setOf("research", "knowledge", "memory", "files"), "Sen titiz bir araştırmacısın. Birden fazla kaynak bul, kaynak bağlantısını belirt, emin olmadığını söyle, bulguları bilgi merkezine kaydet."),
        Role("telefon", "Telefon ajanı", setOf("phone", "memory"), "Sen telefon yönetim uzmanısın. Önce hedefi (kişi/uygulama) doğru belirle; geri alınamaz işlemlerde kullanıcı onayına güven, sonucu açıkça bildir."),
        Role("test", "Test ajanı", setOf("code", "files", "quality", "monitor"), "Sen kalite mühendisisin. Özellikleri test et, başarısızlıkları raporla, test edilmeyeni tamamlanmış gösterme.")
    )
    fun get(id: String) = all.firstOrNull { it.id == id }

    /** Görev başlığından rol tahmini (anahtar kelime); belirsizse kod ajanı. */
    fun guess(title: String): String {
        val t = title.lowercase(java.util.Locale("tr", "TR"))
        return when {
            listOf("araştır", "incele", "kaynak", "karşılaştır").any { t.contains(it) } -> "arastirma"
            listOf("test", "doğrula", "kontrol et").any { t.contains(it) } -> "test"
            listOf("telefon", "mesaj", "alarm", "ara ", "uygulama aç", "hatırlat").any { t.contains(it) } -> "telefon"
            else -> "kod"
        }
    }
}
