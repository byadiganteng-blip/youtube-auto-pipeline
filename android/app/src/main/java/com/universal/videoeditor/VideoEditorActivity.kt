package com.universal.videoeditor

import android.app.ProgressDialog
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File

class VideoEditorActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_editor)

        val btnTrim = findViewById<Button>(R.id.btnTrim)
        val btnCrop = findViewById<Button>(R.id.btnCrop)
        val btnRotate = findViewById<Button>(R.id.btnRotate)
        val btnFlip = findViewById<Button>(R.id.btnFlip)
        val btnSpeed = findViewById<Button>(R.id.btnSpeed)
        val btnVolume = findViewById<Button>(R.id.btnVolume)
        val btnFilter = findViewById<Button>(R.id.btnFilter)
        val btnExtractAudio = findViewById<Button>(R.id.btnExtractAudio)
        val btnCompress = findViewById<Button>(R.id.btnCompress)
        val btnConvertFormat = findViewById<Button>(R.id.btnConvertFormat)

        btnTrim.setOnClickListener { showTrimDialog() }
        btnCrop.setOnClickListener { showCropDialog() }
        btnRotate.setOnClickListener { showRotateDialog() }
        btnFlip.setOnClickListener { showFlipDialog() }
        btnSpeed.setOnClickListener { showSpeedDialog() }
        btnVolume.setOnClickListener { showVolumeDialog() }
        btnFilter.setOnClickListener { showFilterDialog() }
        btnExtractAudio.setOnClickListener { showExtractAudioDialog() }
        btnCompress.setOnClickListener { showCompressDialog() }
        btnConvertFormat.setOnClickListener { showConvertDialog() }
    }

    private fun getVideoPath(): String? {
        val prefs = getSharedPreferences("yad_editor", MODE_PRIVATE)
        return prefs.getString("selected_video", null)
    }

    private fun runFfmpeg(title: String, args: List<String>) {
        val pd = ProgressDialog(this).apply {
            setTitle(title)
            setMessage("Processing...")
            setCancelable(false)
            show()
        }

        FFmpegKit.executeAsync(args.joinToString(" ")) { session ->
            runOnUiThread {
                pd.dismiss()
                val rc = session.returnCode
                if (ReturnCode.isSuccess(rc)) {
                    Toast.makeText(this, "✅ Selesai", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "❌ Gagal: ${session.failStackTrace}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showTrimDialog() {
        val path = getVideoPath() ?: return
        val view = layoutInflater.inflate(R.layout.dialog_input_two, null)
        val et1 = view.findViewById<EditText>(R.id.etValue1)
        val et2 = view.findViewById<EditText>(R.id.etValue2)
        et1.hint = "Start (detik)"
        et2.hint = "Durasi (detik)"

        AlertDialog.Builder(this)
            .setTitle("✂️ Trim Video")
            .setView(view)
            .setPositiveButton("Trim") { _, _ ->
                val start = et1.text.toString().ifEmpty { "0" }
                val dur = et2.text.toString().ifEmpty { "10" }
                val out = File(getExternalFilesDir(null), "trim_${System.currentTimeMillis()}.mp4")
                runFfmpeg("Trim", listOf("-y", "-i", ""$path"", "-ss", start, "-t", dur, "-c", "copy", ""${out.absolutePath}""))
            }
            .setNegativeButton("Batal", null).show()
    }

    private fun showCropDialog() {
        val path = getVideoPath() ?: return
        val view = layoutInflater.inflate(R.layout.dialog_input_two, null)
        val et1 = view.findViewById<EditText>(R.id.etValue1)
        val et2 = view.findViewById<EditText>(R.id.etValue2)
        et1.hint = "Width (px)"
        et2.hint = "Height (px)"

        AlertDialog.Builder(this)
            .setTitle("✂️ Crop Video")
            .setView(view)
            .setPositiveButton("Crop") { _, _ ->
                val w = et1.text.toString().ifEmpty { "720" }
                val h = et2.text.toString().ifEmpty { "1280" }
                val out = File(getExternalFilesDir(null), "crop_${System.currentTimeMillis()}.mp4")
                runFfmpeg("Crop", listOf("-y", "-i", ""$path"", "-vf", "crop=$w:$h", "-c:a", "copy", ""${out.absolutePath}""))
            }
            .setNegativeButton("Batal", null).show()
    }

    private fun showRotateDialog() {
        val path = getVideoPath() ?: return
        val options = arrayOf("90°", "180°", "270°")
        AlertDialog.Builder(this).setTitle("🔄 Rotate")
            .setItems(options) { _, which ->
                val deg = when (which) { 0 -> "90"; 1 -> "180"; else -> "270" }
                val out = File(getExternalFilesDir(null), "rot_${System.currentTimeMillis()}.mp4")
                runFfmpeg("Rotate", listOf("-y", "-i", ""$path"", "-vf", "transpose=$deg", "-c:a", "copy", ""${out.absolutePath}""))
            }.show()
    }

    private fun showFlipDialog() {
        val path = getVideoPath() ?: return
        val options = arrayOf("Horizontal", "Vertical")
        AlertDialog.Builder(this).setTitle("🪞 Flip")
            .setItems(options) { _, which ->
                val filter = if (which == 0) "hflip" else "vflip"
                val out = File(getExternalFilesDir(null), "flip_${System.currentTimeMillis()}.mp4")
                runFfmpeg("Flip", listOf("-y", "-i", ""$path"", "-vf", filter, "-c:a", "copy", ""${out.absolutePath}""))
            }.show()
    }

    private fun showSpeedDialog() {
        val path = getVideoPath() ?: return
        val options = arrayOf("0.5x", "1.5x", "2x", "3x", "4x")
        AlertDialog.Builder(this).setTitle("⚡ Speed")
            .setItems(options) { _, which ->
                val speed = arrayOf("0.5", "1.5", "2", "3", "4")[which]
                val out = File(getExternalFilesDir(null), "speed_${System.currentTimeMillis()}.mp4")
                runFfmpeg("Speed", listOf("-y", "-i", ""$path"", "-filter:v", "setpts=$speed*PTS", "-filter:a", "atempo=$speed", ""${out.absolutePath}""))
            }.show()
    }

    private fun showVolumeDialog() {
        val path = getVideoPath() ?: return
        val view = layoutInflater.inflate(R.layout.dialog_input_one, null)
        val et1 = view.findViewById<EditText>(R.id.etValue)
        et1.hint = "Volume (contoh: 2.0 = 2x)"
        AlertDialog.Builder(this).setTitle("🔊 Volume").setView(view)
            .setPositiveButton("OK") { _, _ ->
                val vol = et1.text.toString().ifEmpty { "1.5" }
                val out = File(getExternalFilesDir(null), "vol_${System.currentTimeMillis()}.mp4")
                runFfmpeg("Volume", listOf("-y", "-i", ""$path"", "-filter:a", "volume=$vol", "-c:v", "copy", ""${out.absolutePath}""))
            }.setNegativeButton("Batal", null).show()
    }

    private fun showFilterDialog() {
        val path = getVideoPath() ?: return
        val options = arrayOf("Grayscale", "Vintage", "Sepia", "Negative", "Blur")
        AlertDialog.Builder(this).setTitle("🎨 Filter").setItems(options) { _, w ->
            val filter = when (w) {
                0 -> "hue=s=0"
                1 -> "curves=vintage"
                2 -> "colorchannelmixer=.393:.769:.189:0:.349:.686:.168:0:.272:.534:.131"
                3 -> "negate"
                4 -> "boxblur=10"
                else -> "null"
            }
            val out = File(getExternalFilesDir(null), "filter_${System.currentTimeMillis()}.mp4")
            runFfmpeg("Filter", listOf("-y", "-i", ""$path"", "-vf", filter, ""${out.absolutePath}""))
        }.show()
    }

    private fun showExtractAudioDialog() {
        val path = getVideoPath() ?: return
        val out = File(getExternalFilesDir(null), "audio_${System.currentTimeMillis()}.mp3")
        runFfmpeg("Extract Audio", listOf("-y", "-i", ""$path"", "-vn", "-acodec", "mp3", ""${out.absolutePath}""))
    }

    private fun showCompressDialog() {
        val path = getVideoPath() ?: return
        val options = arrayOf("Low (360p)", "Medium (720p)", "High (1080p)")
        AlertDialog.Builder(this).setTitle("📦 Compress").setItems(options) { _, w ->
            val crf = arrayOf("32", "28", "23")[w]
            val out = File(getExternalFilesDir(null), "comp_${System.currentTimeMillis()}.mp4")
            runFfmpeg("Compress", listOf("-y", "-i", ""$path"", "-c:v", "libx264", "-crf", crf, "-preset", "fast", "-c:a", "aac", ""${out.absolutePath}""))
        }.show()
    }

    private fun showConvertDialog() {
        val path = getVideoPath() ?: return
        val options = arrayOf("MP4", "MKV", "AVI", "MOV", "GIF")
        AlertDialog.Builder(this).setTitle("🔄 Convert").setItems(options) { _, w ->
            val ext = arrayOf("mp4", "mkv", "avi", "mov", "gif")[w]
            val out = File(getExternalFilesDir(null), "conv_${System.currentTimeMillis()}.$ext")
            runFfmpeg("Convert", listOf("-y", "-i", ""$path"", ""${out.absolutePath}""))
        }.show()
    }
}
