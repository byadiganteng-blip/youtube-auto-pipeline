package com.yadiganteng.pipeline

object SecureConfig {
    private const val GH_OBF = "Bxbf7CTU7zCzeaedAQUCuLgZSJ1511XKlNlTnCN8WH4mT56HF8CyIg=="
    private const val YT_OBF = ""
    private const val API_OBF = "ITfV0gPBniukULKMACgrt5wMeKkV+xDjgeFyhC1JbWpWN/zLY42u"

    private val KEY = byteArrayOf(96, 126, 175, 179, 80, 184, 221, 97, 203, 50, 254, 237, 48, 75, 110, 221, 203, 75, 14, 238, 33, 161, 102, 141, 242, 149, 32, 230, 89, 10, 60, 9)

    private fun deobf(enc: String): String {
        if (enc.isEmpty()) return ""
        try {
            val d = android.util.Base64.decode(enc, android.util.Base64.DEFAULT)
            val r = ByteArray(d.size)
            for (i in d.indices) r[i] = (d[i].toInt() xor KEY[i % KEY.size].toInt()).toByte()
            return String(r, Charsets.UTF_8)
        } catch (e: Exception) { return "" }
    }

    fun getGithubToken(): String = deobf(GH_OBF)
    fun getYoutubeToken(): String = deobf(YT_OBF)
    fun getApiKey(): String = deobf(API_OBF)
}
