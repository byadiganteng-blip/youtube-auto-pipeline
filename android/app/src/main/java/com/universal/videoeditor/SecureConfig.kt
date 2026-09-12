package com.universal.videoeditor

import android.content.Context
import android.content.SharedPreferences

object SecureConfig {
    private const val PREF = "yadapp_secure"
    @Volatile private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            synchronized(this) {
                if (prefs == null) {
                    prefs = context.applicationContext
                        .getSharedPreferences(PREF, Context.MODE_PRIVATE)
                }
            }
        }
    }

    private fun p(): SharedPreferences? = prefs

    fun getGithubToken(): String = p()?.getString("gh_token", "") ?: ""
    fun setGithubToken(t: String) { p()?.edit()?.putString("gh_token", t)?.apply() }
    fun clearGithubToken() { p()?.edit()?.remove("gh_token")?.apply() }

    fun getAdminEmail(): String =
        p()?.getString("admin_email", "admin@local") ?: "admin@local"
    fun setAdminEmail(e: String) { p()?.edit()?.putString("admin_email", e)?.apply() }
    fun clearAdmin() { p()?.edit()?.remove("admin_email")?.apply() }

    fun getString(key: String, def: String = ""): String =
        p()?.getString(key, def) ?: def
    fun setString(key: String, v: String) { p()?.edit()?.putString(key, v)?.apply() }
    fun getBool(key: String, def: Boolean = false): Boolean =
        p()?.getBoolean(key, def) ?: def
    fun setBool(key: String, v: Boolean) { p()?.edit()?.putBoolean(key, v)?.apply() }
    fun getInt(key: String, def: Int = 0): Int = p()?.getInt(key, def) ?: def
    fun setInt(key: String, v: Int) { p()?.edit()?.putInt(key, v)?.apply() }
}
