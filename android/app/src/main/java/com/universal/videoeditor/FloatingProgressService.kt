package com.universal.videoeditor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlin.math.abs

class FloatingProgressService : Service() {

    companion object {
        const val EXTRA_PCT = "pct"
        const val EXTRA_LABEL = "label"
        const val EXTRA_DETAIL = "detail"
        const val ACTION_SHOW = "show"
        const val ACTION_UPDATE = "update"
        const val ACTION_HIDE = "hide"

        private const val CHANNEL_ID = "float_service"
        private const val NOTIF_ID = 9999

        @Volatile private var instance: FloatingProgressService? = null
        @Volatile private var foregroundStarted = false

        fun show(c: Context, pct: Int, label: String, detail: String) {
            try {
                val i = Intent(c, FloatingProgressService::class.java).apply {
                    action = ACTION_SHOW
                    putExtra(EXTRA_PCT, pct)
                    putExtra(EXTRA_LABEL, label)
                    putExtra(EXTRA_DETAIL, detail)
                }
                if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i)
                else c.startService(i)
            } catch (e: Exception) {
                LogTracker.e(c, "Float", "show err: ${e.message}")
            }
        }

        fun update(c: Context, pct: Int, label: String, detail: String) {
            try {
                val i = Intent(c, FloatingProgressService::class.java).apply {
                    action = ACTION_UPDATE
                    putExtra(EXTRA_PCT, pct)
                    putExtra(EXTRA_LABEL, label)
                    putExtra(EXTRA_DETAIL, detail)
                }
                c.startService(i)
            } catch (e: Exception) {
                LogTracker.e(c, "Float", "update err: ${e.message}")
            }
        }

        fun hide(c: Context) {
            try {
                c.startService(Intent(c, FloatingProgressService::class.java).apply { action = ACTION_HIDE })
            } catch (_: Exception) {}
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var floatView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        createChannel()
        // WAJIB: panggil startForeground() segera setelah onCreate
        // untuk memenuhi syarat Android 8+
        try {
            val notif = buildNotification(0, "Memulai…")
            startForeground(NOTIF_ID, notif)
            foregroundStarted = true
            LogTracker.i(this, "Float", "startForeground OK")
        } catch (e: Exception) {
            LogTracker.e(this, "Float", "startForeground err: ${e.message}")
        }
    }

    private fun createChannel() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                    val ch = NotificationChannel(
                        CHANNEL_ID, "Cliper On Progress",
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = "Progress proses video"
                        setShowBadge(false)
                    }
                    nm.createNotificationChannel(ch)
                }
            }
        } catch (e: Exception) {
            LogTracker.e(this, "Float", "channel err: ${e.message}")
        }
    }

    private fun buildNotification(pct: Int, label: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0))

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Cliper On • $pct%")
            .setContentText(label)
            .setProgress(100, pct, false)
            .setOngoing(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
    }

    private fun updateForegroundNotif(pct: Int, label: String) {
        if (!foregroundStarted) return
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIF_ID, buildNotification(pct, label))
        } catch (_: Exception) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        mainHandler.post {
            try {
                when (intent?.action) {
                    ACTION_SHOW -> {
                        val pct = intent.getIntExtra(EXTRA_PCT, 0)
                        val lbl = intent.getStringExtra(EXTRA_LABEL) ?: ""
                        val det = intent.getStringExtra(EXTRA_DETAIL) ?: ""
                        showFloating(pct, lbl, det)
                        updateForegroundNotif(pct, lbl)
                    }
                    ACTION_UPDATE -> {
                        val pct = intent.getIntExtra(EXTRA_PCT, 0)
                        val lbl = intent.getStringExtra(EXTRA_LABEL) ?: ""
                        val det = intent.getStringExtra(EXTRA_DETAIL) ?: ""
                        updateFloating(pct, lbl, det)
                        updateForegroundNotif(pct, lbl)
                    }
                    ACTION_HIDE -> hideFloating()
                }
            } catch (e: Exception) {
                LogTracker.e(this, "Float", "onStartCommand err: ${e.message}")
            }
        }
        return START_STICKY
    }

    private fun showFloating(pct: Int, label: String, detail: String) {
        if (floatView != null) {
            updateFloating(pct, label, detail)
            return
        }
        try {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            floatView = LayoutInflater.from(this).inflate(R.layout.floating_progress, null)

            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE

            layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 30
                y = 200
            }

            var initX = 0
            var initY = 0
            var touchX = 0f
            var touchY = 0f

            floatView?.setOnTouchListener { _, event ->
                try {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            initX = layoutParams!!.x
                            initY = layoutParams!!.y
                            touchX = event.rawX
                            touchY = event.rawY
                            true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            layoutParams!!.x = initX + (event.rawX - touchX).toInt()
                            layoutParams!!.y = initY + (event.rawY - touchY).toInt()
                            windowManager?.updateViewLayout(floatView, layoutParams)
                            true
                        }
                        MotionEvent.ACTION_UP -> {
                            abs(event.rawX - touchX) < 10 && abs(event.rawY - touchY) < 10
                        }
                        else -> false
                    }
                } catch (_: Exception) { false }
            }

            windowManager?.addView(floatView, layoutParams)
            LogTracker.i(this, "Float", "addView OK — view attached")
            updateFloating(pct, label, detail)
        } catch (e: Exception) {
            LogTracker.e(this, "Float", "addView failed: ${e.message}")
            floatView = null
        }
    }

    private fun updateFloating(pct: Int, label: String, detail: String) {
        try {
            val v = floatView ?: return
            v.findViewById<ProgressBar>(R.id.fpProgress)?.progress = pct
            v.findViewById<TextView>(R.id.fpPercent)?.text = "$pct%"
            v.findViewById<TextView>(R.id.fpLabel)?.text = label
            v.findViewById<TextView>(R.id.fpDetail)?.text = detail
        } catch (_: Exception) {}
    }

    private fun hideFloating() {
        try {
            floatView?.let {
                try { windowManager?.removeView(it) } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
        floatView = null
        try {
            if (foregroundStarted) {
                stopForeground(true)
                foregroundStarted = false
            }
        } catch (_: Exception) {}
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        try { hideFloating() } catch (_: Exception) {}
    }
}
