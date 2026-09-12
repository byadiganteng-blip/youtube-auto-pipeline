package com.universal.videoeditor

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Video Editor
 * Created by KARYADI, Coding by KARYADI
 */
class VideoEditorActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_video_editor)
        } catch (e: Exception) {
            Toast.makeText(this, "Layout error", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
