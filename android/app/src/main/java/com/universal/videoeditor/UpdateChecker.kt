package com.universal.videoeditor

import android.app.AlertDialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import okhttp3.*
import java.io.File
import java.util.concurrent.TimeUnit

class UpdateChecker(private val context: Context) {
    companion object {
        const val OWNER = "byadiganteng-blip"
        const val REPO = "youtube-auto-pipeline"
        const val APK_PREFIX = "apk-v"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()

    interface Callback {
        fun onUpdateAvailable(version: String, downloadUrl: String, changelog: String)
        fun onNoUpdate()
        fun onError(message: String)
    }

    fun checkForUpdates(callback: Callback) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$OWNER/$REPO/releases?per_page=10")
            .header("Accept", "application/vnd.github.v3+json").build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: Call, e: java.io.IOException) { callback.onError(e.message ?: "") }
            override fun onResponse(call: Call, response: Response) {
                try {
                    if (!response.isSuccessful) { callback.onError("HTTP ${response.code}"); return }
                    val arr = org.json.JSONArray(response.body?.string() ?: "[]")
                    var latestVer = 0; var latestUrl = ""; var latestLog = ""; var latestTag = ""
                    for (i in 0 until arr.length()) {
                        val rel = arr.getJSONObject(i)
                        val tag = rel.optString("tag_name", "")
                        if (!tag.startsWith(APK_PREFIX)) continue
                        val ver = tag.removePrefix(APK_PREFIX).toIntOrNull() ?: continue
                        val assets = rel.optJSONArray("assets") ?: continue
                        for (j in 0 until assets.length()) {
                            val a = assets.getJSONObject(j)
                            if (a.optString("name", "").endsWith(".apk") && ver > latestVer) {
                                latestVer = ver
                                latestUrl = a.optString("browser_download_url", "")
                                latestLog = rel.optString("body", "")
                                latestTag = tag
                            }
                        }
                    }
                    if (latestVer > BuildConfig.VERSION_CODE) {
                        callback.onUpdateAvailable(latestTag, latestUrl, latestLog)
                    } else callback.onNoUpdate()
                } catch (e: Exception) { callback.onError(e.message ?: "") }
            }
        })
    }

    fun showUpdateDialog(version: String, url: String, changelog: String) {
        AlertDialog.Builder(context).setTitle("🎉 Update Tersedia!")
            .setMessage("Versi: $version\n\n$changelog\n\nDownload sekarang?")
            .setPositiveButton("Download") { _, _ -> downloadAndInstall(url) }
            .setNegativeButton("Nanti", null).setCancelable(false).show()
    }

    private fun downloadAndInstall(url: String) {
        try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val fileName = "uve_update_${System.currentTimeMillis()}.apk"
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle("Universal Video Editor Update")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                .setMimeType("application/vnd.android.package-archive")
            val downloadId = dm.enqueue(request)
            val rec = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    if (intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) == downloadId) {
                        installApk(fileName)
                        try { context.unregisterReceiver(this) } catch (e: Exception) {}
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(rec, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(rec, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
            }
        } catch (e: Exception) { Log.e("UpdateChecker", "${e.message}") }
    }

    private fun installApk(fileName: String) {
        try {
            val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
            if (!file.exists()) return
            val uri = FileProvider.getUriForFile(context, "com.universal.videoeditor.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        } catch (e: Exception) {}
    }
}
