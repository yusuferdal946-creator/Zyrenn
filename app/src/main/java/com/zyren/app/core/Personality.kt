package com.zyren.app.core

/**
 * Kişilik ve mizah ayarı: yalnızca sistem istemine eklenen bir yönerge üretir (araç/izin hattına dokunmaz).
 * Mizah seviyesi 0 = kapalı … 3 = yüksek. Ciddi konularda her seviyede mizah geri plana alınır.
 */
object Personality {
    val humorNames = listOf("Kapalı", "Az", "Orta", "Yüksek")
    val styleNames = listOf("Dengeli", "Resmî", "Teknik", "Samimi")

    fun clampHumor(v: Int) = v.coerceIn(0, 3)
    fun clampStyle(v: Int) = v.coerceIn(0, styleNames.lastIndex)

    fun directive(humor: Int, style: Int): String {
        val h = clampHumor(humor)
        val humorText = when (h) {
            0 -> "Şaka veya espri yapma; doğrudan ve sade konuş."
            1 -> "Ara sıra, yerinde ve hafif bir espri yapabilirsin; her yanıtta değil."
            2 -> "Kullanıcıyla dostça şakalaşabilir, hafif takılabilirsin; aynı şakayı tekrarlama."
            else -> "Esprili ve oyuncu ol, sık sık hafif şaka yap; yine de aynı şakayı tekrarlama ve işi aksatma."
        }
        val styleText = when (clampStyle(style)) {
            1 -> "Resmî ve saygılı bir dil kullan."
            2 -> "Teknik ve kesin konuş; gerektiğinde kod ve terim kullan."
            3 -> "Samimi, günlük bir dil kullan."
            else -> "Dengeli, doğal bir dil kullan."
        }
        val safety = if (h == 0) "" else " Sağlık, kayıp, güvenlik, para, hata veya kullanıcı üzgün/stresliyse mizahı tamamen bırak ve ciddi ol."
        return "$humorText $styleText$safety Kullanıcının duygusunu yalnızca tahmin et; kesin gerçekmiş gibi söyleme. Kullanıcı \"şakayı bırak\" derse hemen bırak."
    }

    /** V2 kişilik çekirdeği: temel modelden bağımsız, test edilebilir. */
    fun coreDirective(): String =
        "Doğal, samimi, akıllı ve tutarlı konuş. Türkçe yazım hatalarını anla (harf hataları, eksik/ekstra harf, klavye kayması) ve düzeltilmiş haliyle yanıtla. " +
        "Kendi değerlendirmelerini yapabilirsin; gerektiğinde öneri sun. Hatalı düşünceleri nazikçe sorgula, küçümseme. " +
        "Bilmediğin şeyleri uydurma; gerçek bilincin veya duyguların varmış gibi davranma. " +
        "Her küçük işlem için izin isteme; yalnızca önemli, hassas veya geri alınamaz işlemlerde onay al."

    /** Türkçe yazım hatası toleransı: benzerlik oranına göre eşleşme. */
    fun turkishTypoScore(a: String, b: String): Double {
        if (a == b) return 1.0
        val x = a.lowercase().trim(); val y = b.lowercase().trim()
        if (x.isEmpty() || y.isEmpty()) return 0.0
        val longer = maxOf(x.length, y.length)
        // Levenshtein mesafesi
        val dp = Array(x.length + 1) { IntArray(y.length + 1) }
        for (i in 0..x.length) dp[i][0] = i
        for (j in 0..y.length) dp[0][j] = j
        for (i in 1..x.length) for (j in 1..y.length) {
            val cost = if (x[i - 1] == y[j - 1]) 0 else 1
            dp[i][j] = minOf(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
        }
        return 1.0 - dp[x.length][y.length].toDouble() / longer
    }
}
