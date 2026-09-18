package com.universal.videoeditor

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ProgressBar
import android.widget.TextView
import kotlin.math.abs

/**
 * FloatingProgressService — floating window bisa digeser,
 * tampil di atas aplikasi lain (seperti chat head).
 */
class FloatingProgressService : Service() {

    companion object {
        const val EXTRA_PCT = "pct"
        const val EXTRA_LABEL = "label"
        const val EXTRA_DETAIL = "detail"
        const val ACTION_SHOW = "show"
        const val ACTION_UPDATE = "update"
        const val ACTION_HIDE = "hide"

        fun show(c: Context, pct: Int, label: String, detail: String) {
            val i = Intent(c, FloatingProgressService::class.java).apply {
                action = ACTION_SHOW
                putExtra(EXTRA_PCT, pct)
                putExtra(EXTRA_LABEL, label)
                putExtra(EXTRA_DETAIL, detail)
            }
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i) else c.startService(i)
        }

        fun update(c: Context, pct: Int, label: String, detail: String) {
            val i = Intent(c, FloatingProgressService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_PCT, pct)
                putExtra(EXTRA_LABEL, label)
                putExtra(EXTRA_DETAIL, detail)
            }
            c.startService(i)
        }

        fun hide(c: Context) {
            val i = Intent(c, FloatingProgressService::class.java).apply { action = ACTION_HIDE }
            c.startService(i)
        }
    }

    private var windowManager: WindowManager? = null
    private var floatView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> {
                val pct = intent.getIntExtra(EXTRA_PCT, 0)
                val lbl = intent.getStringExtra(EXTRA_LABEL) ?: ""
                val det = intent.getStringExtra(EXTRA_DETAIL) ?: ""
                showFloating(pct, lbl, det)
            }
            ACTION_UPDATE -> {
                val pct = intent.getIntExtra(EXTRA_PCT, 0)
                val lbl = intent.getStringExtra(EXTRA_LABEL) ?: ""
                val det = intent.getStringExtra(EXTRA_DETAIL) ?: ""
                updateFloating(pct, lbl, det)
            }
            ACTION_HIDE -> hideFloating()
        }
        return START_NOT_STICKY
    }

    private fun showFloating(pct: Int, label: String, detail: String) {
        if (floatView != null) {
            updateFloating(pct, label, detail)
            return
        }
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        floatView = LayoutInflater.from(this).inflate(R.layout.floating_progress, null)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_PHONE

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 30
            y = 200
        }

        // Drag behavior
        var initX = 0
        var initY = 0
        var touchX = 0f
        var touchY = 0f

        floatView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initX = layoutParams!!.x
                    initY = layoutParams!!.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    layoutParams!!.x = initX + dx
                    layoutParams!!.y = initY + dy
                    windowManager?.updateViewLayout(floatView, layoutParams)
                    true
                }
                else -> abs(event.rawX - touchX) < 10 && abs(event.rawY - touchY) < 10
            }
        }

        try {
            windowManager?.addView(floatView, layoutParams)
        } catch (e: Exception) {
            LogTracker.e(this, "Float", "addView failed: ${e.message}")
        }
        updateFloating(pct, label, detail)
        LogTracker.i(this, "Float", "Floating shown")
    }

    private fun updateFloating(pct: Int, label: String, detail: String) {
        floatView?.let { v ->
            v.findViewById<ProgressBar>(R.id.fpProgress)?.progress = pct
            v.findViewById<TextView>(R.id.fpPercent)?.text = "$pct%"
            v.findViewById<TextView>(R.id.fpLabel)?.text = label
            v.findViewById<TextView>(R.id.fpDetail)?.text = detail
        }
    }

    private fun hideFloating() {
        floatView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
        }
        floatView = null
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        hideFloating()
    }
}
