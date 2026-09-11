package com.universal.videoeditor

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object AdminManager {
    private const val ADMIN_EMAIL = "ynuraini686@gmail.com"
    private const val ADMIN_PASS = "imadmin"
    private const val PREFS_NAME = "yad_admin_prefs"
    private const val KEY_IS_ADMIN = "is_admin"
    private const val KEY_ADMIN_LOGIN_TIME = "admin_login_time"

    private fun getPrefs(context: Context): SharedPreferences {
        val mk = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedSharedPreferences.create(
            context, PREFS_NAME, mk,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    }

    fun login(context: Context, email: String, password: String): Boolean {
        if (email.trim().equals(ADMIN_EMAIL, ignoreCase = true) && password.trim() == ADMIN_PASS) {
            getPrefs(context).edit()
                .putBoolean(KEY_IS_ADMIN, true)
                .putLong(KEY_ADMIN_LOGIN_TIME, System.currentTimeMillis()).apply()
            return true
        }
        return false
    }

    fun isAdmin(context: Context) = getPrefs(context).getBoolean(KEY_IS_ADMIN, false)
    fun getLoginTime(context: Context) = getPrefs(context).getLong(KEY_ADMIN_LOGIN_TIME, 0L)
    fun logout(context: Context) { getPrefs(context).edit().clear().apply() }
    fun shouldAutoLogout(context: Context): Boolean {
        val t = getLoginTime(context)
        return t > 0 && (System.currentTimeMillis() - t) > 60 * 60 * 1000
    }
}
