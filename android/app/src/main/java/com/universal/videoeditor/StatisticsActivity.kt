package com.universal.videoeditor

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class StatisticsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try { setContentView(R.layout.activity_statistics) } catch (e: Exception) { finish() }
    }
}
