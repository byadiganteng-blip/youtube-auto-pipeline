package com.universal.videoeditor

import android.app.Application
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class YadApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Global crash handler — tulis crash ke log sebelum app mati
        try {
            val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                try {
                    writeCrashLog(throwable)
                } catch (_: Exception) {}
                // Panggil handler default (biar Android tetap show dialog / restart)
                defaultHandler?.uncaughtException(thread, throwable)
            }
        } catch (_: Exception) {}

        LogTracker.writeHeader(this)
        LogTracker.i(this, "YadApp", "Application onCreate")

        if (SecureConfig.token(this).isNullOrEmpty() && BuildConfig.GH_TOKEN.isNotEmpty()) {
            SecureConfig.save(this, BuildConfig.GH_TOKEN, BuildConfig.GH_USER)
            LogTracker.i(this, "YadApp", "Token auto-injected from BuildConfig")
        }
    }

    private fun writeCrashLog(t: Throwable) {
        try {
            val dir = getExternalFilesDir("logs") ?: File(filesDir, "logs")
            if (!dir.exists()) dir.mkdirs()
            val date = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
            val f = File(dir, "crash_${date}.log")
            val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
            val sb = StringBuilder()
            sb.append("\n=== CRASH at $ts ===\n")
            sb.append("Thread: ${Thread.currentThread().name}\n")
            sb.append("Exception: ${t.javaClass.name}\n")
            sb.append("Message: ${t.message}\n")
            sb.append("Stack trace:\n")
            sb.append(t.stackTraceToString())
            sb.append("\nCause:\n")
            var cause = t.cause
            while (cause != null) {
                sb.append("  ← ${cause.javaClass.name}: ${cause.message}\n")
                cause = cause.cause
            }
            sb.append("=====================\n\n")
            f.appendText(sb.toString())
        } catch (_: Exception) {}
    }

    companion object {
        const val OWNER = "byadiganteng-blip"
        const val REPO = "youtube-auto-pipeline"
        const val WORKFLOW = "pipeline.yml"
        const val DOWNLOAD_DIR = "CliperOn"
        const val APP_NAME = "Cliper On"
        const val DEV_NAME = "YsDev"
    }
}
