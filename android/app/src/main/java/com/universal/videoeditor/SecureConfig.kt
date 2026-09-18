package com.universal.videoeditor

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * SecureConfig — simpan token terenkripsi (Android 6+) 
 * Fallback ke SharedPreferences biasa kalau EncryptedSharedPreferences gagal.
 */
object SecureConfig {
    private const val FILE = "cliper_on_secure"
    private const val FILE_FALLBACK = "cliper_on_prefs"
    private const val KEY_TOKEN = "tk"
    private const val KEY_USER = "us"

    private fun encrypted(c: Context): SharedPreferences? {
        return try {
            EncryptedSharedPreferences.create(
                c, FILE,
                MasterKey.Builder(c)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            LogTracker.w(c, "Secure", "EncryptedPrefs failed, fallback: ${e.message}")
            null
        }
    }

    private fun fallback(c: Context): SharedPreferences =
        c.getSharedPreferences(FILE_FALLBACK, Context.MODE_PRIVATE)

    private fun prefs(c: Context): SharedPreferences =
        encrypted(c) ?: fallback(c)

    fun save(c: Context, token: String, user: String) {
        try {
            prefs(c).edit().putString(KEY_TOKEN, token).putString(KEY_USER, user).apply()
        } catch (e: Exception) {
            LogTracker.e(c, "Secure", "save failed: ${e.message}")
            // Ultimate fallback
            try {
                fallback(c).edit().putString(KEY_TOKEN, token).putString(KEY_USER, user).apply()
            } catch (_: Exception) {}
        }
    }

    fun token(c: Context): String? = try {
        prefs(c).getString(KEY_TOKEN, null)
    } catch (e: Exception) {
        try { fallback(c).getString(KEY_TOKEN, null) } catch (_: Exception) { null }
    }

    fun user(c: Context): String? = try {
        prefs(c).getString(KEY_USER, null)
    } catch (e: Exception) {
        try { fallback(c).getString(KEY_USER, null) } catch (_: Exception) { null }
    }

    fun clear(c: Context) {
        try { prefs(c).edit().clear().apply() } catch (_: Exception) {}
        try { fallback(c).edit().clear().apply() } catch (_: Exception) {}
    }
}
