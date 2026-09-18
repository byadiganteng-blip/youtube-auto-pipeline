package com.universal.videoeditor

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

object UpdateChecker {
    private const val TAG = "Update"
    private const val VERSION_URL = "https://raw.githubusercontent.com/byadiganteng-blip/youtube-auto-pipeline/main/version.json"

    data class Info(
        val versionName: String,
        val versionCode: Int,
        val buildNumber: Int,
        val apkUrl: String,
        val releaseUrl: String,
        val notes: String,
        val forceUpdate: Boolean
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun check(c: Context): Info? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(VERSION_URL)
                .header("User-Agent", "CliperOn")
                .header("Cache-Control", "no-cache")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    LogTracker.w(c, TAG, "HTTP ${resp.code}")
                    return@withContext null
                }
                val json = JSONObject(resp.body?.string() ?: "{}")
                val info = Info(
                    versionName = json.optString("versionName", "0"),
                    versionCode = json.optInt("versionCode", 0),
                    buildNumber = json.optInt("buildNumber", 0),
                    apkUrl = json.optString("apkUrl", ""),
                    releaseUrl = json.optString("releaseUrl", ""),
                    notes = json.optString("releaseNotes", ""),
                    forceUpdate = json.optBoolean("forceUpdate", false)
                )
                val currentVc = c.packageManager.getPackageInfo(c.packageName, 0).versionCode
                LogTracker.i(c, TAG, "Server vc=${info.versionCode} current vc=$currentVc")
                if (info.versionCode > currentVc) info else null
            }
        } catch (e: Exception) {
            LogTracker.e(c, TAG, "Check failed: ${e.message}")
            null
        }
    }

    fun downloadApk(c: Context, info: Info) {
        try {
            val url = info.apkUrl
            if (url.isEmpty()) {
                Toast.makeText(c, "URL APK tidak tersedia", Toast.LENGTH_SHORT).show()
                return
            }
            val fileName = "CliperOn-v${info.versionName}.apk"
            val dm = c.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val req = DownloadManager.Request(Uri.parse(url))
                .setTitle("Update Cliper On v${info.versionName}")
                .setDescription("Mengunduh update...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                .setMimeType("application/vnd.android.package-archive")
            val id = dm.enqueue(req)
            LogTracker.i(c, TAG, "Download started id=$id")

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                    if (downloadId != id) return
                    LogTracker.i(ctx, TAG, "Download completed, launching install")
                    val file = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        fileName
                    )
                    if (file.exists()) installApk(ctx, file)
                    try { ctx.unregisterReceiver(this) } catch (_: Exception) {}
                }
            }
            if (Build.VERSION.SDK_INT >= 33) {
                c.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                    Context.RECEIVER_EXPORTED)
            } else {
                c.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
            }
        } catch (e: Exception) {
            LogTracker.e(c, TAG, "Download failed: ${e.message}")
            Toast.makeText(c, "Gagal mengunduh: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun installApk(c: Context, file: File) {
        try {
            val apkUri: Uri = if (Build.VERSION.SDK_INT >= 24) {
                androidx.core.content.FileProvider.getUriForFile(
                    c, "${c.packageName}.fileprovider", file)
            } else {
                Uri.fromFile(file)
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            c.startActivity(intent)
        } catch (e: Exception) {
            LogTracker.e(c, TAG, "Install failed: ${e.message}")
            Toast.makeText(c, "Buka file manual di Downloads: ${file.name}",
                Toast.LENGTH_LONG).show()
        }
    }
}
