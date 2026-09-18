package com.universal.videoeditor

import android.content.Context
import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * LogTracker — simpan log lengkap aktivitas app.
 * Lokasi: /storage/emulated/0/Android/data/com.universal.videoeditor/files/logs/
 * (diakses via `context.getExternalFilesDir("logs")`)
 */
object LogTracker {
    private const val TAG = "CliperOn"
    private const val MAX_LINES = 500
    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    private fun logDir(c: Context): File {
        val dir = c.getExternalFilesDir("logs") ?: File(c.filesDir, "logs")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun todayFile(c: Context): File {
        val date = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        return File(logDir(c), "cliper_on_${date}.log")
    }

    @Synchronized
    fun log(c: Context, level: String, tag: String, message: String) {
        val line = "${fmt.format(Date())}  [$level]  $tag — $message\n"
        try {
            val file = todayFile(c)
            // Trim jika file > MAX_LINES
            if (file.exists() && file.readLines().size > MAX_LINES) {
                val lines = file.readLines().takeLast(MAX_LINES / 2)
                file.writeText(lines.joinToString("\n") + "\n")
            }
            file.appendText(line)
        } catch (t: Throwable) { /* silent */ }
    }

    fun i(c: Context, tag: String, msg: String) = log(c, "I", tag, msg)
    fun w(c: Context, tag: String, msg: String) = log(c, "W", tag, msg)
    fun e(c: Context, tag: String, msg: String) = log(c, "E", tag, msg)
    fun d(c: Context, tag: String, msg: String) = log(c, "D", tag, msg)

    /** Baca 2000 baris terakhir dari semua file log (gabungan). */
    fun readAll(c: Context, maxLines: Int = 2000): String {
        val dir = logDir(c)
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".log") }
            ?.sortedBy { it.name } ?: return "(tidak ada log)"
        val sb = StringBuilder()
        for (f in files) {
            sb.append("══════ ${f.name} ══════\n")
            try {
                val lines = f.readLines()
                val start = maxOf(0, lines.size - maxLines / files.size)
                sb.append(lines.subList(start, lines.size).joinToString("\n"))
            } catch (e: Exception) {
                sb.append("(gagal baca: ${e.message})")
            }
            sb.append("\n\n")
        }
        return sb.toString().ifBlank { "(log kosong)" }
    }

    /** Path yang bisa di-share ke user. */
    fun logPath(c: Context): String {
        val dir = c.getExternalFilesDir("logs")
        return dir?.absolutePath ?: "(internal)"
    }

    /** Hapus semua log. */
    fun clear(c: Context) {
        val dir = logDir(c)
        dir.listFiles()?.forEach { it.delete() }
        log(c, "I", TAG, "Log cleared by user")
    }

    /** Info device + app untuk header log. */
    fun writeHeader(c: Context) {
        val pi = c.packageManager.getPackageInfo(c.packageName, 0)
        log(c, "I", TAG, "===== SESSION START =====")
        log(c, "I", TAG, "App: ${pi.versionName} (code ${pi.versionCode})")
        log(c, "I", TAG, "Package: ${c.packageName}")
        log(c, "I", TAG, "Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        log(c, "I", TAG, "Android: ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})")
        log(c, "I", TAG, "Storage: ${Environment.getExternalStorageDirectory()}")
    }
}
