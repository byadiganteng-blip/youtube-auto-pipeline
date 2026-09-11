package com.yadiganteng.pipeline

import android.util.Base64

/**
 * Auto-generated config dengan token tertanam.
 * Token di-obfuscate dengan XOR + SHA256.
 * 
 * CREATED BY KARYADI CODING KARYADI
 */
object SecureConfig {

    // Token GitHub (obfuscated, base64)
    private const val GH_OBF = "Bxbf7CTU7zCzeaedAQUCuLgZSJ1511XKlNlTnCN8WH4mT56HF8CyIg=="

    // XOR key sebagai String CSV, di-parse ke ByteArray di runtime
    // Menghindari error "integer literal does not conform to Byte"
    private const val KEY_CSV = "96,126,175,179,80,184,221,97,203,50,254,237,48,75,110,221,203,75,14,238,33,161,102,141,242,149,32,230,89,10,60,9"

    private fun getKey(): ByteArray {
        val parts = KEY_CSV.split(",")
        val result = ByteArray(parts.size)
        for (i in parts.indices) {
            result[i] = parts[i].trim().toInt().toByte()
        }
        return result
    }

    private fun deobf(encoded: String): String {
        if (encoded.isEmpty()) return ""
        return try {
            val decoded = Base64.decode(encoded, Base64.DEFAULT)
            val key = getKey()
            val result = ByteArray(decoded.size)
            for (i in decoded.indices) {
                result[i] = (decoded[i].toInt() xor key[i % key.size].toInt()).toByte()
            }
            String(result, Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }

    fun getGithubToken(): String = deobf(GH_OBF)
    fun getYoutubeToken(): String = ""
    fun getApiKey(): String = ""
}
