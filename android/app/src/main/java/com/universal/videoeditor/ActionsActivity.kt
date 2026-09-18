package com.universal.videoeditor
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import org.json.JSONObject
class ActionsActivity : AppCompatActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_actions)
        val tv = findViewById<TextView>(R.id.tvActionsOutput)
        fun load() { lifecycleScope.launch {
            tv.text = "Memuat..."
            val r = AdminApi.listRuns(this@ActionsActivity)
            if (!r.ok) { tv.text = "❌ ${r.code}"; return@launch }
            val arr = JSONObject(r.body).optJSONArray("workflow_runs") ?: return@launch
            val sb = StringBuilder()
            for (i in 0 until minOf(arr.length(), 10)) {
                val o = arr.getJSONObject(i)
                sb.append("#${o.optLong("run_number")} [${o.optString("status")}] ${o.optString("conclusion","-")}\n")
            }
            tv.text = sb.toString().ifBlank { "Kosong" }
        }}
        findViewById<MaterialButton>(R.id.btnRefreshRuns).setOnClickListener { load() }
        findViewById<MaterialButton>(R.id.btnBackActions).setOnClickListener { finish() }
        load()
    }
}
