package com.universal.videoeditor

import android.content.Context
import android.content.SharedPreferences

/**
 * Konfigurasi terenkripsi sederhana (SharedPreferences).
 * Untuk produksi, ganti dengan EncryptedSharedPreferences.
 */
object SecureConfig {
    private const val PREF = "yadapp_secure"
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    }

    fun getGithubToken(): String = prefs.getString("gh_token", "") ?: ""
    fun setGithubToken(t: String) = prefs.edit().putString("gh_token", t).apply()
    fun clearGithubToken() = prefs.edit().remove("gh_token").apply()

    fun getAdminEmail(): String = prefs.getString("admin_email", "admin@local") ?: "admin@local"
    fun setAdminEmail(e: String) = prefs.edit().putString("admin_email", e).apply()
    fun clearAdmin() = prefs.edit().remove("admin_email").apply()
}
