package com.yadiganteng.pipeline

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    companion object {
        const val OWNER = "byadiganteng-blip"
        const val REPO = "youtube-auto-pipeline"
        const val WORKFLOW = "pipeline.yml"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS).build()

    private lateinit var etUrl: EditText
    private lateinit var etPartDuration: EditText
    private lateinit var etDelay: EditText
    private lateinit var etStartPart: EditText
    private lateinit var spinnerProcessMode: Spinner
    private lateinit var spinnerVideoSize: Spinner
    private lateinit var spinnerVideoQuality: Spinner
    private lateinit var spinnerMethod: Spinner
    private lateinit var spinnerPrivacy: Spinner
    private lateinit var spinnerAutoUpload: Spinner
    private lateinit var spinnerUploadType: Spinner
    private lateinit var btnOpenActions: Button
    private lateinit var btnCheckStatus: Button
    private lateinit var btnOpenFiles: Button
    private lateinit var btnOpenActionsView: Button
    private lateinit var btnCredit: Button
    private lateinit var btnRunWorkflow: Button
    private lateinit var btnReset: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvTokenInfo: TextView
    private lateinit var progressBar: ProgressBar

    private var githubToken: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etUrl = findViewById(R.id.etUrl)
        etPartDuration = findViewById(R.id.etPartDuration)
        etDelay = findViewById(R.id.etDelay)
        etStartPart = findViewById(R.id.etStartPart)
        spinnerProcessMode = findViewById(R.id.spinnerProcessMode)
        spinnerVideoSize = findViewById(R.id.spinnerVideoSize)
        spinnerVideoQuality = findViewById(R.id.spinnerVideoQuality)
        spinnerMethod = findViewById(R.id.spinnerMethod)
        spinnerPrivacy = findViewById(R.id.spinnerPrivacy)
        spinnerAutoUpload = findViewById(R.id.spinnerAutoUpload)
        spinnerUploadType = findViewById(R.id.spinnerUploadType)
        btnOpenActions = findViewById(R.id.btnOpenActions)
        btnCheckStatus = findViewById(R.id.btnCheckStatus)
        btnOpenFiles = findViewById(R.id.btnOpenFiles)
        btnOpenActionsView = findViewById(R.id.btnOpenActionsView)
        btnCredit = findViewById(R.id.btnCredit)
        btnRunWorkflow = findViewById(R.id.btnRunWorkflow)
        btnReset = findViewById(R.id.btnLogout)
        tvStatus = findViewById(R.id.tvStatus)
        tvTokenInfo = findViewById(R.id.tvTokenInfo)
        progressBar = findViewById(R.id.progressBar)

        setupSpinners()

        // Auto-token: cek token user (encrypted) → fallback ke embedded
        if (SecurePrefs.hasToken(this)) {
            githubToken = SecurePrefs.getToken(this) ?: ""
            val u = SecurePrefs.getUsername(this)
            tvTokenInfo.text = "🔐 Token user: $u (terenkripsi)"
            tvTokenInfo.setTextColor(resources.getColor(R.color.success, null))
        } else {
            githubToken = SecureConfig.getGithubToken()
            if (githubToken.isNotEmpty()) {
                tvTokenInfo.text = "✅ Token tertanam aktif"
                tvTokenInfo.setTextColor(resources.getColor(R.color.success, null))
                SecurePrefs.saveToken(this, githubToken, "embedded")
            } else {
                tvTokenInfo.text = "⚠️ Token tidak ada"
                tvTokenInfo.setTextColor(resources.getColor(R.color.danger, null))
                btnRunWorkflow.isEnabled = false
            }
        }

        btnOpenActions.setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/$OWNER/$REPO/actions"))) }
        btnCheckStatus.setOnClickListener { checkStatus() }
        btnOpenFiles.setOnClickListener {
            val i = Intent(this, FilesActivity::class.java); i.putExtra("token", githubToken); startActivity(i)
        }
        btnOpenActionsView.setOnClickListener {
            val i = Intent(this, ActionsActivity::class.java); i.putExtra("token", githubToken); startActivity(i)
        }
        btnCredit.setOnClickListener { startActivity(Intent(this, CreditActivity::class.java)) }
        btnRunWorkflow.setOnClickListener { runWorkflow() }
        btnReset.setOnClickListener {
            AlertDialog.Builder(this).setTitle("Reset").setMessage("Hapus token user?")
                .setPositiveButton("Ya") { _, _ ->
                    SecurePrefs.clear(this)
                    githubToken = SecureConfig.getGithubToken()
                    if (githubToken.isNotEmpty()) SecurePrefs.saveToken(this, githubToken, "embedded")
                    recreate()
                }.setNegativeButton("Batal", null).show()
        }
    }

    private fun setupSpinners() {
        spinnerProcessMode.adapter = createAdapter(listOf("remove_watermark", "skip_watermark"))
        spinnerVideoSize.adapter = createAdapter(listOf(
            "original", "yt_shorts", "tiktok", "ig_reels", "fb_reels", "whatsapp_status",
            "ig_feed_square", "ig_feed_portrait", "yt_landscape", "yt_4k", "fb_video", "twitter"))
        spinnerVideoQuality.adapter = createAdapter(listOf(
            "original", "144p", "240p", "360p", "480p", "720p", "1080p", "1440p", "2160p"))
        spinnerMethod.adapter = createAdapter(listOf("blur", "inpaint"))
        spinnerPrivacy.adapter = createAdapter(listOf("public", "unlisted", "private"))
        spinnerAutoUpload.adapter = createAdapter(listOf("false", "true"))
        spinnerUploadType.adapter = createAdapter(listOf("video", "reels"))
    }

    private fun createAdapter(items: List<String>): ArrayAdapter<String> {
        return ArrayAdapter(this, android.R.layout.simple_spinner_item, items).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
    }

    private fun runWorkflow() {
        val url = etUrl.text.toString().trim()
        if (url.isEmpty()) { Toast.makeText(this, "URL wajib", Toast.LENGTH_LONG).show(); return }

        val inputs = JSONObject().apply {
            put("video_url", url)
            put("process_mode", spinnerProcessMode.selectedItem.toString())
            put("video_size", spinnerVideoSize.selectedItem.toString())
            put("video_quality", spinnerVideoQuality.selectedItem.toString())
            put("method", spinnerMethod.selectedItem.toString())
            put("part_duration", etPartDuration.text.toString())
            put("privacy", spinnerPrivacy.selectedItem.toString())
            put("auto_upload", spinnerAutoUpload.selectedItem.toString())
            put("upload_type", spinnerUploadType.selectedItem.toString())
            put("upload_delay", etDelay.text.toString())
            put("start_part", etStartPart.text.toString())
        }
        val json = JSONObject().apply { put("ref", "main"); put("inputs", inputs) }
        val body = json.toString().toRequestBody("application/json".toMediaType())

        val req = Request.Builder()
            .url("https://api.github.com/repos/$OWNER/$REPO/actions/workflows/$WORKFLOW/dispatches")
            .header("Authorization", "token $githubToken")
            .header("Accept", "application/vnd.github.v3+json")
            .post(body).build()

        progressBar.visibility = View.VISIBLE
        tvStatus.text = "Mengirim..."

        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                runOnUiThread { progressBar.visibility = View.GONE; tvStatus.text = "Gagal: ${e.message}" }
            }
            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    tvStatus.text = if (response.code == 204) "✅ Workflow triggered!" else "Error ${response.code}"
                }
            }
        })
    }

    private fun checkStatus() {
        val req = Request.Builder()
            .url("https://api.github.com/repos/$OWNER/$REPO/actions/runs?per_page=1")
            .header("Authorization", "token $githubToken")
            .header("Accept", "application/vnd.github.v3+json").build()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                runOnUiThread { tvStatus.text = "Error: ${e.message}" }
            }
            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    if (response.isSuccessful) {
                        try {
                            val runs = JSONObject(response.body?.string() ?: "{}").optJSONArray("workflow_runs")
                            if (runs != null && runs.length() > 0) {
                                val r = runs.getJSONObject(0)
                                tvStatus.text = "Status: ${r.optString("status")}\nConclusion: ${r.optString("conclusion")}"
                            }
                        } catch (e: Exception) { tvStatus.text = "Error: ${e.message}" }
                    }
                }
            }
        })
    }
}
