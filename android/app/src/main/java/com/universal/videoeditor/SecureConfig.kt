package com.universal.videoeditor

import android.util.Base64

object SecureConfig {
    private const val GH_OBF = "Bxbf7CTU7zCzeaedAQUCuLgZSJ1511XKlNlTnCN8WH4mT56HF8CyIg=="
    private const val KEY_CSV = "96,126,175,179,80,184,221,97,203,50,254,237,48,75,110,221,203,75,14,238,33,161,102,141,242,149,32,230,89,10,60,9"

    private fun getKey(): ByteArray {
        val parts = KEY_CSV.split(",")
        return ByteArray(parts.size) { parts[it].trim().toInt().toByte() }
    }

    private fun deobf(enc: String): String {
        if (enc.isEmpty()) return ""
        return try {
            val d = Base64.decode(enc, Base64.DEFAULT)
            val k = getKey()
            val r = ByteArray(d.size)
            for (i in d.indices) r[i] = (d[i].toInt() xor k[i % k.size].toInt()).toByte()
            String(r, Charsets.UTF_8)
        } catch (e: Exception) { "" }
    }

    fun getGithubToken(): String = deobf(GH_OBF)
    fun getYoutubeToken(): String = ""
    fun getApiKey(): String = ""
}
