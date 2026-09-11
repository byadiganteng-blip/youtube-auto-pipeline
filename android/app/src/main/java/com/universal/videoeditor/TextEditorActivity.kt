package com.universal.videoeditor

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * Editor generik: dipakai untuk Edit Workflow, Tambah Fitur (kotlin-file),
 * dan edit file lain via GitHub API.
 */
class TextEditorActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PATH = "path"
        const val EXTRA_TITLE = "title"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_text_editor)

        val path  = intent.getStringExtra(EXTRA_PATH)  ?: ""
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Edit File"

        findViewById<TextView>(R.id.tvEditorTitle).text = title
        val et = findViewById<EditText>(R.id.etEditorContent)

        lifecycleScope.launch {
            val res = AdminApi.getFile(path)
            if (res != null) {
                et.setText(res.first)
            } else {
                Toast.makeText(this@TextEditorActivity,
                    "Gagal load file: $path", Toast.LENGTH_LONG).show()
            }
        }

        findViewById<Button>(R.id.btnEditorCancel).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnEditorSave).setOnClickListener {
            val content = et.text.toString()
            lifecycleScope.launch {
                val (ok, msg) = AdminApi.updateFile(path, content, "chore: update $path via APK")
                Toast.makeText(this@TextEditorActivity,
                    if (ok) "✅ Tersimpan" else "❌ $msg",
                    Toast.LENGTH_LONG).show()
                if (ok) finish()
            }
        }
    }
}
