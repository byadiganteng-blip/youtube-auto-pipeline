package com.universal.videoeditor

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
    private lateinit var etWatermarkText: EditText
    private lateinit var spinnerProcessMode: Spinner
    private lateinit var spinnerVideoSize: Spinner
    private lateinit var spinnerVideoQuality: Spinner
    private lateinit var spinnerMethod: Spinner
    private lateinit var spinnerWatermarkPosition: Spinner
    private lateinit var spinnerWatermarkStyle: Spinner
    private lateinit var spinnerPrivacy: Spinner
    private lateinit var spinnerAutoUpload: Spinner
    private lateinit var spinnerUploadType: Spinner
    private lateinit var btnCheckStatus: Button
    private lateinit var btnOpenFiles: Button
    private lateinit var btnOpenActionsView: Button
    private lateinit var btnUploadYtToken: Button
    private lateinit var btnUploadApiKey: Button
    private lateinit var btnInstructions: Button
    private lateinit var btnCredit: Button
    private lateinit var btnRunWorkflow: Button
    private lateinit var btnReset: Button
    private lateinit var btnVideoEditor: Button
    private lateinit var btnStatistics: Button
    private lateinit var btnSettings: Button
    private lateinit var layoutTokenUpload: View
    private lateinit var tvStatus: TextView
    private lateinit var tvTokenInfo: TextView
    private lateinit var progressBar: ProgressBar

    private var githubToken: String = ""

    private var tapCount = 0
    private val handler = Handler(Looper.getMainLooper())
    private val tapResetRunnable = Runnable { tapCount = 0 }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (!allGranted) {
            Toast.makeText(this, "Beberapa izin ditolak.", Toast.LENGTH_LONG).show()
        }
    }

    private val ytTokenPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uploadYtToken(it) }
        }
    }

    private val apiKeyPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uploadApiKey(it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestPermissionsIfNeeded()

        etUrl = findViewById(R.id.etUrl)
        etPartDuration = findViewById(R.id.etPartDuration)
        etDelay = findViewById(R.id.etDelay)
        etStartPart = findViewById(R.id.etStartPart)
        etWatermarkText = findViewById(R.id.etWatermarkText)
        spinnerProcessMode = findViewById(R.id.spinnerProcessMode)
        spinnerVideoSize = findViewById(R.id.spinnerVideoSize)
        spinnerVideoQuality = findViewById(R.id.spinnerVideoQuality)
        spinnerMethod = findViewById(R.id.spinnerMethod)
        spinnerWatermarkPosition = findViewById(R.id.spinnerWatermarkPosition)
        spinnerWatermarkStyle = findViewById(R.id.spinnerWatermarkStyle)
        spinnerPrivacy = findViewById(R.id.spinnerPrivacy)
        spinnerAutoUpload = findViewById(R.id.spinnerAutoUpload)
        spinnerUploadType = findViewById(R.id.spinnerUploadType)
        btnCheckStatus = findViewById(R.id.btnCheckStatus)
        btnOpenFiles = findViewById(R.id.btnOpenFiles)
        btnOpenActionsView = findViewById(R.id.btnOpenActionsView)
        btnUploadYtToken = findViewById(R.id.btnUploadYtToken)
        btnUploadApiKey = findViewById(R.id.btnUploadApiKey)
        btnInstructions = findViewById(R.id.btnInstructions)
        btnCredit = findViewById(R.id.btnCredit)
        btnRunWorkflow = findViewById(R.id.btnRunWorkflow)
        btnReset = findViewById(R.id.btnLogout)
        btnVideoEditor = findViewById(R.id.btnVideoEditor)
        btnStatistics = findViewById(R.id.btnStatistics)
        btnSettings = findViewById(R.id.btnSettings)
        layoutTokenUpload = findViewById(R.id.layoutTokenUpload)
        tvStatus = findViewById(R.id.tvStatus)
        tvTokenInfo = findViewById(R.id.tvTokenInfo)
        progressBar = findViewById(R.id.progressBar)

        setupSpinners()
        updateTokenInfoUI()
        setupAdminTrigger()

        Handler(Looper.getMainLooper()).postDelayed({ checkForAppUpdate() }, 2000)

        spinnerAutoUpload.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                updateTokenUploadVisibility()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        btnCheckStatus.setOnClickListener { checkStatus() }
        btnOpenFiles.setOnClickListener {
            val i = Intent(this, FilesActivity::class.java)
            i.putExtra("token", githubToken)
            startActivity(i)
        }
        btnOpenActionsView.setOnClickListener {
            val i = Intent(this, ActionsActivity::class.java)
            i.putExtra("token", githubToken)
            startActivity(i)
        }
        btnVideoEditor.setOnClickListener {
            startActivity(Intent(this, VideoEditorActivity::class.java))
        }
        btnStatistics.setOnClickListener {
            startActivity(Intent(this, StatisticsActivity::class.java))
        }
        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        btnUploadYtToken.setOnClickListener { openYtTokenPicker() }
        btnUploadApiKey.setOnClickListener { openApiKeyPicker() }
        btnInstructions.setOnClickListener {
            startActivity(Intent(this, InstructionsActivity::class.java))
        }
        btnCredit.setOnClickListener {
            startActivity(Intent(this, CreditActivity::class.java))
        }
        btnRunWorkflow.setOnClickListener { runWorkflow() }
        btnReset.setOnClickListener {
            AlertDialog.Builder(this).setTitle("Reset")
                .setMessage("Hapus token user?")
                .setPositiveButton("Ya") { _, _ ->
                    SecurePrefs.clear(this)
                    githubToken = SecureConfig.getGithubToken()
                    if (githubToken.isNotEmpty()) SecurePrefs.saveToken(this, githubToken, "embedded")
                    recreate()
                }.setNegativeButton("Batal", null).show()
        }
    }

    private fun requestPermissionsIfNeeded() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, "android.permission.POST_NOTIFICATIONS")
                != PackageManager.PERMISSION_GRANTED) {
                needed.add("android.permission.POST_NOTIFICATIONS")
            }
            if (ContextCompat.checkSelfPermission(this, "android.permission.READ_MEDIA_VIDEO")
                != PackageManager.PERMISSION_GRANTED) {
                needed.add("android.permission.READ_MEDIA_VIDEO")
            }
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
    }

    private fun setupAdminTrigger() {
        val headerArea = findViewById<View>(R.id.adminTapArea)
        headerArea?.setOnClickListener {
            tapCount++
            handler.removeCallbacks(tapResetRunnable)
            handler.postDelayed(tapResetRunnable, 2000)
            if (tapCount >= 5) {
                tapCount = 0
                handler.removeCallbacks(tapResetRunnable)
                showAdminLoginDialog()
            }
        }
    }

    private fun showAdminLoginDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_admin_login, null)
        val etEmail = view.findViewById<EditText>(R.id.etAdminEmail)
        val etPass = view.findViewById<EditText>(R.id.etAdminPassword)

        AlertDialog.Builder(this).setTitle("🔐 Login").setView(view)
            .setPositiveButton("Login") { _, _ ->
                if (AdminManager.login(this, etEmail.text.toString(), etPass.text.toString())) {
                    Toast.makeText(this, "✅ Welcome Admin!", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, AdminActivity::class.java))
                } else {
                    Toast.makeText(this, "❌ Salah", Toast.LENGTH_SHORT).show()
                }
            }.setNegativeButton("Batal", null).show()
    }

    private fun checkForAppUpdate() {
        try {
            val checker = UpdateChecker(this)
            checker.checkForUpdates(object : UpdateChecker.Callback {
                override fun onUpdateAvailable(version: String, downloadUrl: String, changelog: String) {
                    runOnUiThread { checker.showUpdateDialog(version, downloadUrl, changelog) }
                }
                override fun onNoUpdate() {}
                override fun onError(message: String) {}
            })
        } catch (e: Exception) {}
    }

    private fun updateTokenUploadVisibility() {
        val autoUpload = spinnerAutoUpload.selectedItem?.toString() ?: "false"
        layoutTokenUpload.visibility = if (autoUpload == "true") View.VISIBLE else View.GONE
    }

    private fun updateTokenInfoUI() {
        if (SecurePrefs.hasToken(this)) {
            githubToken = SecurePrefs.getToken(this) ?: ""
            val u = SecurePrefs.getUsername(this)
            tvTokenInfo.text = "🔐 Token: $u"
            tvTokenInfo.setTextColor(resources.getColor(R.color.success, null))
        } else {
            githubToken = SecureConfig.getGithubToken()
            if (githubToken.isNotEmpty()) {
                tvTokenInfo.text = "✅ Token aktif"
                tvTokenInfo.setTextColor(resources.getColor(R.color.success, null))
                SecurePrefs.saveToken(this, githubToken, "embedded")
            } else {
                tvTokenInfo.text = "⚠️ Token tidak ada"
                tvTokenInfo.setTextColor(resources.getColor(R.color.danger, null))
                btnRunWorkflow.isEnabled = false
            }
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
        spinnerWatermarkPosition.adapter = createAdapter(listOf(
            "bottom_right", "bottom_left", "top_right", "top_left", "bottom_center", "top_center"))
        spinnerWatermarkStyle.adapter = createAdapter(listOf(
            "minimal", "elegant", "bold", "neon", "gradient"))
        spinnerPrivacy.adapter = createAdapter(listOf("public", "unlisted", "private"))
        spinnerAutoUpload.adapter = createAdapter(listOf("false", "true"))
        spinnerUploadType.adapter = createAdapter(listOf("video", "reels"))
        updateTokenUploadVisibility()
    }

    private fun createAdapter(items: List<String>): ArrayAdapter<String> {
        return ArrayAdapter(this, android.R.layout.simple_spinner_item, items).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
    }

    private fun openYtTokenPicker() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "text/*"
        }
        ytTokenPicker.launch(i)
    }

    private fun uploadYtToken(uri: Uri) {
        try {
            val content = contentResolver.openInputStream(uri)!!.bufferedReader().readText().trim()
            if (content.isEmpty()) {
                Toast.makeText(this, "File kosong", Toast.LENGTH_SHORT).show(); return
            }
            val file = java.io.File(filesDir, "youtube_token.txt")
            file.writeText(content)
            Toast.makeText(this, "✅ YouTube Token tersimpan", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun openApiKeyPicker() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "text/*"
        }
        apiKeyPicker.launch(i)
    }

    private fun uploadApiKey(uri: Uri) {
        try {
            val content = contentResolver.openInputStream(uri)!!.bufferedReader().readText().trim()
            if (!content.startsWith("AIzaSy")) {
                Toast.makeText(this, "Format salah", Toast.LENGTH_LONG).show(); return
            }
            val file = java.io.File(filesDir, "api_key.txt")
            file.writeText(content)
            Toast.makeText(this, "✅ API Key tersimpan", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun runWorkflow() {
        val url = etUrl.text.toString().trim()
        if (url.isEmpty()) {
            Toast.makeText(this, "URL wajib diisi", Toast.LENGTH_LONG).show(); return
        }

        val autoUpload = spinnerAutoUpload.selectedItem.toString()
        if (autoUpload == "true") {
            val ytTokenFile = java.io.File(filesDir, "youtube_token.txt")
            if (!ytTokenFile.exists() || ytTokenFile.readText().isEmpty()) {
                AlertDialog.Builder(this).setTitle("⚠️ Token YouTube belum ada")
                    .setMessage("Upload YouTube Token dulu?")
                    .setPositiveButton("Upload") { _, _ -> openYtTokenPicker() }
                    .setNegativeButton("Skip", null).show()
                return
            }
        }

        val inputs = JSONObject().apply {
            put("video_url", url)
            put("process_mode", spinnerProcessMode.selectedItem.toString())
            put("video_size", spinnerVideoSize.selectedItem.toString())
            put("video_quality", spinnerVideoQuality.selectedItem.toString())
            put("method", spinnerMethod.selectedItem.toString())
            put("part_duration", etPartDuration.text.toString())
            put("privacy", spinnerPrivacy.selectedItem.toString())
            put("auto_upload", autoUpload)
            put("upload_type", spinnerUploadType.selectedItem.toString())
            put("upload_delay", etDelay.text.toString())
            put("start_part", etStartPart.text.toString())
            put("watermark_text", etWatermarkText.text.toString())
            put("watermark_position", spinnerWatermarkPosition.selectedItem.toString())
            put("watermark_style", spinnerWatermarkStyle.selectedItem.toString())
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
                StatisticsActivity.incrementRun(this@MainActivity, false)
            }
            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    val success = response.code == 204
                    tvStatus.text = if (success) "✅ Triggered!" else "Error ${response.code}"
                    StatisticsActivity.incrementRun(this@MainActivity, success)
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
                    if (response.isSuccessful) try {
                        val runs = JSONObject(response.body?.string() ?: "{}").optJSONArray("workflow_runs")
                        if (runs != null && runs.length() > 0) {
                            val r = runs.getJSONObject(0)
                            tvStatus.text = "Status: ${r.optString("status")}\nConclusion: ${r.optString("conclusion")}"
                        }
                    } catch (e: Exception) { tvStatus.text = "Error: ${e.message}" }
                }
            }
        })
    }
}
