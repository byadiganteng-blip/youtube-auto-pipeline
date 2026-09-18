package com.universal.videoeditor

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * NotifFetcher — Ambil notifikasi dari GitHub (raw notif.json).
 * Cache di SharedPreferences untuk offline.
 */
object NotifFetcher {
    private const val TAG = "Notif"
    private const val PREF = "notif_cache"
    private const val KEY_JSON = "cached_json"
    private const val KEY_TS = "cached_ts"

    data class Notif(
        val id: String,
        val title: String,
        val message: String,
        val icon: String,
        val link: String,
        val linkLabel: String,
        val color: String,
        val active: Boolean
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Ambil notif dari network. Fallback ke cache kalau gagal.
     */
    suspend fun fetch(c: Context): List<Notif> = withContext(Dispatchers.IO) {
        // Coba network
        try {
            val url = c.getString(R.string.notif_url)
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "CliperOn")
                .header("Cache-Control", "no-cache")
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    LogTracker.d(c, TAG, "Fetched ${body.length} bytes")
                    saveCache(c, body)
                    return@withContext parse(c, body)
                } else {
                    LogTracker.w(c, TAG, "HTTP ${resp.code}")
                }
            }
        } catch (e: Exception) {
            LogTracker.w(c, TAG, "Network failed: ${e.message}")
        }
        // Fallback cache
        val cached = loadCache(c)
        if (cached != null) {
            LogTracker.i(c, TAG, "Using cached notif")
            parse(c, cached)
        } else emptyList()
    }

    private fun parse(c: Context, json: String): List<Notif> {
        return try {
            val root = JSONObject(json)
            val arr = root.optJSONArray("notifications") ?: return emptyList()
            val out = mutableListOf<Notif>()
            val currentVc = try {
                    if (android.os.Build.VERSION.SDK_INT >= 33) {
                        c.packageManager.getPackageInfo(c.packageName,
                            android.content.pm.PackageManager.PackageInfoFlags.of(0)).longVersionCode.toInt()
                    } else {
                        @Suppress("DEPRECATION")
                        c.packageManager.getPackageInfo(c.packageName, 0).versionCode
                    }
                } catch (_: Exception) { 0 }
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val active = o.optBoolean("active", true)
                val minVc = o.optInt("minVersion", 1)
                if (active && currentVc >= minVc) {
                    out.add(Notif(
                        id = o.optString("id", "n$i"),
                        title = o.optString("title", ""),
                        message = o.optString("message", ""),
                        icon = o.optString("icon", "🔔"),
                        link = o.optString("link", ""),
                        linkLabel = o.optString("linkLabel", "Buka"),
                        color = o.optString("color", "#10B981"),
                        active = active
                    ))
                }
            }
            LogTracker.i(c, TAG, "Parsed ${out.size} notif")
            out
        } catch (e: Exception) {
            LogTracker.e(c, TAG, "Parse error: ${e.message}")
            emptyList()
        }
    }

    private fun saveCache(c: Context, json: String) {
        try {
            c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                .putString(KEY_JSON, json)
                .putLong(KEY_TS, System.currentTimeMillis())
                .apply()
        } catch (_: Exception) {}
    }

    private fun loadCache(c: Context): String? {
        return try {
            c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_JSON, null)
        } catch (_: Exception) { null }
    }
}
