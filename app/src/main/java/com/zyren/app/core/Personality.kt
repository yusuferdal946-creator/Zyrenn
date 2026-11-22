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
}
