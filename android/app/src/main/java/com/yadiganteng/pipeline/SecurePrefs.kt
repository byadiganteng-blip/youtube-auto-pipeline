package com.yadiganteng.pipeline

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SecurePrefs {
    private const val P = "yad_secure_prefs"
    private const val K = "gh_token_encrypted"
    private const val KT = "token_saved_at"
    private const val KU = "gh_username"

    private fun p(c: Context): SharedPreferences {
        val mk = MasterKey.Builder(c).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedSharedPreferences.create(c, P, mk,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    }
    fun saveToken(c: Context, t: String, u: String = "") {
        p(c).edit().putString(K, t).putString(KU, u).putLong(KT, System.currentTimeMillis()).apply()
    }
    fun getToken(c: Context): String? = p(c).getString(K, null)
    fun getUsername(c: Context): String = p(c).getString(KU, "") ?: ""
    fun getSavedAt(c: Context): Long = p(c).getLong(KT, 0L)
    fun hasToken(c: Context): Boolean = !getToken(c).isNullOrEmpty()
    fun clear(c: Context) { p(c).edit().clear().apply() }
}
