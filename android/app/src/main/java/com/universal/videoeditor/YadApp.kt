package com.universal.videoeditor

import android.app.Application
import android.util.Log

/**
 * Application class — init semua komponen.
 *
 * Created by KARYADI, Coding by KARYADI
 */
class YadApp : Application() {

    override fun onCreate() {
        super.onCreate()
        try {
            SecureConfig.init(this)
            Log.d("YadApp", "SecureConfig initialized")
        } catch (e: Exception) {
            Log.e("YadApp", "Init failed: ${e.message}", e)
        }
    }
}
