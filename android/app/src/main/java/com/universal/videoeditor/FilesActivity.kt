package com.universal.videoeditor
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import org.json.JSONObject
class FilesActivity : AppCompatActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_files)
        val tv = findViewById<TextView>(R.id.tvFilesOutput)
        findViewById<MaterialButton>(R.id.btnListArtifacts).setOnClickListener { lifecycleScope.launch {
            tv.text = "Memuat..."
            val runs = AdminApi.listRuns(this@FilesActivity)
            if (!runs.ok) { tv.text = "❌"; return@launch }
            val ra = JSONObject(runs.body).optJSONArray("workflow_runs") ?: return@launch
            if (ra.length() == 0) { tv.text = "Tidak ada"; return@launch }
            val r = AdminApi.listArtifacts(this@FilesActivity, ra.getJSONObject(0).optLong("id"))
            if (!r.ok) { tv.text = "❌"; return@launch }
            val arts = JSONObject(r.body).optJSONArray("artifacts") ?: return@launch
            val sb = StringBuilder()
            for (i in 0 until arts.length()) {
                val a = arts.getJSONObject(i)
                sb.append("${a.optString("name")} (${a.optLong("size_in_bytes")/1024} KB)\n")
            }
            tv.text = sb.toString().ifBlank { "Kosong" }
        }}
        findViewById<MaterialButton>(R.id.btnListReleases).setOnClickListener { lifecycleScope.launch {
            tv.text = "Memuat..."
            val r = AdminApi.listReleases(this@FilesActivity)
            if (!r.ok) { tv.text = "❌"; return@launch }
            val arr = JSONObject("{\"x\":${r.body}}").getJSONArray("x")
            val sb = StringBuilder()
            for (i in 0 until arr.length()) sb.append("${arr.getJSONObject(i).optString("tag_name")}\n")
            tv.text = sb.toString().ifBlank { "Kosong" }
        }}
        findViewById<MaterialButton>(R.id.btnBackFiles).setOnClickListener { finish() }
    }
}
