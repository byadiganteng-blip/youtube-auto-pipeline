package com.universal.videoeditor
import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
object SecureConfig {
    private const val FILE = "cliper_on_secure"
    private fun p(c: Context) = EncryptedSharedPreferences.create(c, FILE,
        MasterKey.Builder(c).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    fun save(c: Context, t: String, u: String) = p(c).edit().putString("tk",t).putString("us",u).apply()
    fun token(c: Context): String? = p(c).getString("tk", null)
    fun user(c: Context): String? = p(c).getString("us", null)
    fun clear(c: Context) = p(c).edit().clear().apply()
}
