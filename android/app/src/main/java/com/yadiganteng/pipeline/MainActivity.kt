package com.yadiganteng.pipeline

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    companion object {
        const val PREFS_NAME = "yad_pipeline_prefs"
        const val KEY_TOKEN = "github_token"
        const val OWNER = "byadiganteng-blip"
        const val REPO = "youtube-auto-pipeline"
        const val WORKFLOW = "pipeline.yml"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private lateinit var layoutSetup: View
    private lateinit var layoutForm: View
    private lateinit var etToken: EditText
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
    private lateinit var btnUploadTokenFile: Button
    private lateinit var btnSaveToken: Button
    private lateinit var btnLogout: Button
    private lateinit var btnOpenActions: Button
    private lateinit var btnCheckStatus: Button
    private lateinit var btnOpenFiles: Button
    private lateinit var btnRunWorkflow: Button
    private lateinit var tvStatus: TextView
    private lateinit var progressBar: ProgressBar

    private var githubToken: String = ""

    private val tokenFilePicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { readTokenFromFile(it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        layoutSetup = findViewById(R.id.layoutSetup)
        layoutForm = findViewById(R.id.layoutForm)
        etToken = findViewById(R.id.etToken)
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
        btnUploadTokenFile = findViewById(R.id.btnUploadTokenFile)
        btnSaveToken = findViewById(R.id.btnSaveToken)
        btnLogout = findViewById(R.id.btnLogout)
        btnOpenActions = findViewById(R.id.btnOpenActions)
        btnCheckStatus = findViewById(R.id.btnCheckStatus)
        btnOpenFiles = findViewById(R.id.btnOpenFiles)
        btnRunWorkflow = findViewById(R.id.btnRunWorkflow)
        tvStatus = findViewById(R.id.tvStatus)
        progressBar = findViewById(R.id.progressBar)

        setupSpinners()
        loadToken()

        btnUploadTokenFile.setOnClickListener { openTokenFilePicker() }
        btnSaveToken.setOnClickListener { saveToken() }
        btnLogout.setOnClickListener { logout() }
        btnOpenActions.setOnClickListener { openActions() }
        btnCheckStatus.setOnClickListener { checkStatus() }
        btnOpenFiles.setOnClickListener {
            val intent = Intent(this, FilesActivity::class.java)
            intent.putExtra("token", githubToken)
            startActivity(intent)
        }
        btnRunWorkflow.setOnClickListener { runWorkflow() }
    }

    private fun setupSpinners() {
        spinnerProcessMode.adapter = createAdapter(listOf("remove_watermark", "skip_watermark"))
        spinnerVideoSize.adapter = createAdapter(listOf(
            "original", "yt_shorts", "tiktok", "ig_reels", "fb_reels", "whatsapp_status",
            "ig_feed_square", "ig_feed_portrait", "yt_landscape", "yt_4k", "fb_video", "twitter"
        ))
        spinnerVideoQuality.adapter = createAdapter(listOf(
            "original", "144p", "240p", "360p", "480p", "720p", "1080p", "1440p", "2160p"
        ))
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

    private fun openTokenFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/*"
        }
        tokenFilePicker.launch(intent)
    }

    private fun readTokenFromFile(uri: Uri) {
        try {
            val content = contentResolver.openInputStream(uri)!!.bufferedReader().readText().trim()
            val token = extractToken(content)
            if (token.isNotEmpty()) {
                etToken.setText(token)
                Toast.makeText(this, "Token dibaca", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun extractToken(content: String): String {
        try {
            val json = JSONObject(content)
            if (json.has("github_token")) return json.getString("github_token").trim()
        } catch (_: Exception) {}
        val cleaned = content.replace("\n", "").replace("\r", "").replace(" ", "")
        if (cleaned.startsWith("ghp_") || cleaned.startsWith("github_pat_")) return cleaned
        return content.lines().firstOrNull {
            it.trim().startsWith("ghp_") || it.trim().startsWith("github_pat_")
        }?.trim() ?: ""
    }

    private fun loadToken() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        githubToken = prefs.getString(KEY_TOKEN, "") ?: ""
        if (githubToken.isNotEmpty()) {
            etToken.setText(githubToken)
            showForm()
        } else showSetup()
    }

    private fun saveToken() {
        val token = etToken.text.toString().trim()
        if (token.isEmpty()) return
        showLoading(true)
        verifyToken(token) { ok ->
            showLoading(false)
            if (ok) {
                githubToken = token
                getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { putString(KEY_TOKEN, token) }
                Toast.makeText(this, "Token VALID", Toast.LENGTH_SHORT).show()
                showForm()
            } else Toast.makeText(this, "Token INVALID", Toast.LENGTH_LONG).show()
        }
    }

    private fun verifyToken(token: String, callback: (Boolean) -> Unit) {
        val request = Request.Builder().url("https://api.github.com/user")
            .header("Authorization", "token $token")
            .header("Accept", "application/vnd.github.v3+json").build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) { runOnUiThread { callback(false) } }
            override fun onResponse(call: Call, response: Response) { runOnUiThread { callback(response.isSuccessful) } }
        })
    }

    private fun logout() {
        AlertDialog.Builder(this).setTitle("Logout").setMessage("Hapus token?")
            .setPositiveButton("Ya") { _, _ ->
                getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { remove(KEY_TOKEN) }
                githubToken = ""
                etToken.setText("")
                showSetup()
            }.setNegativeButton("Batal", null).show()
    }

    private fun runWorkflow() {
        val url = etUrl.text.toString().trim()
        if (url.isEmpty()) {
            Toast.makeText(this, "URL wajib diisi", Toast.LENGTH_LONG).show()
            return
        }
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
        val apiUrl = "https://api.github.com/repos/$OWNER/$REPO/actions/workflows/$WORKFLOW/dispatches"
        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(apiUrl)
            .header("Authorization", "token $githubToken")
            .header("Accept", "application/vnd.github.v3+json")
            .post(body).build()
        showLoading(true)
        setStatus("Mengirim...")
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                runOnUiThread { showLoading(false); setStatus("Gagal: ${e.message}") }
            }
            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    showLoading(false)
                    setStatus(if (response.code == 204) "✅ Triggered!" else "Error ${response.code}")
                }
            }
        })
    }

    private fun checkStatus() {
        val url = "https://api.github.com/repos/$OWNER/$REPO/actions/runs?per_page=1"
        val request = Request.Builder().url(url)
            .header("Authorization", "token $githubToken")
            .header("Accept", "application/vnd.github.v3+json").build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                runOnUiThread { setStatus("Error: ${e.message}") }
            }
            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    if (response.isSuccessful) {
                        try {
                            val runs = JSONObject(response.body?.string() ?: "{}").optJSONArray("workflow_runs")
                            if (runs != null && runs.length() > 0) {
                                val run = runs.getJSONObject(0)
                                setStatus("Status: ${run.optString("status")}\nConclusion: ${run.optString("conclusion")}")
                            }
                        } catch (e: Exception) { setStatus("Error: ${e.message}") }
                    }
                }
            }
        })
    }

    private fun openActions() {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/$OWNER/$REPO/actions")))
    }

    private fun showSetup() { layoutSetup.visibility = View.VISIBLE; layoutForm.visibility = View.GONE }
    private fun showForm() { layoutSetup.visibility = View.GONE; layoutForm.visibility = View.VISIBLE }
    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        btnRunWorkflow.isEnabled = !show
    }
    private fun setStatus(text: String) { tvStatus.text = text }
}
