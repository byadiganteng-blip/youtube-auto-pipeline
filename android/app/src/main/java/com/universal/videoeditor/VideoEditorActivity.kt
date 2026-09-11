package com.universal.videoeditor

import android.app.ProgressDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import nl.bravobit.ffmpeg.ExecuteBinaryResponseHandler
import nl.bravobit.ffmpeg.FFmpeg
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class VideoEditorActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "VideoEditor"
        private const val PICK_VIDEO = 1001
    }

    private lateinit var ffmpeg: FFmpeg
    private var inputVideoPath: String? = null
    private lateinit var tvSelectedVideo: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_editor)

        // Cek FFmpeg support
        ffmpeg = FFmpeg.getInstance(this)
        if (!ffmpeg.isSupported) {
            AlertDialog.Builder(this)
                .setTitle("⚠️ Tidak Support")
                .setMessage("FFmpeg tidak support di device ini. Aplikasi akan tetap bisa upload via server.")
                .setPositiveButton("OK", null)
                .show()
        }

        tvSelectedVideo = findViewById(R.id.tvSelectedVideo)
        val btnSelectVideo = findViewById<Button>(R.id.btnSelectVideo)

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

        btnSelectVideo.setOnClickListener { pickVideo() }
        btnTrim.setOnClickListener { requireVideo { showTrimDialog() } }
        btnCrop.setOnClickListener { requireVideo { showCropDialog() } }
        btnRotate.setOnClickListener { requireVideo { showRotateDialog() } }
        btnFlip.setOnClickListener { requireVideo { showFlipDialog() } }
        btnSpeed.setOnClickListener { requireVideo { showSpeedDialog() } }
        btnVolume.setOnClickListener { requireVideo { showVolumeDialog() } }
        btnFilter.setOnClickListener { requireVideo { showFilterDialog() } }
        btnExtractAudio.setOnClickListener { requireVideo { showExtractAudioDialog() } }
        btnCompress.setOnClickListener { requireVideo { showCompressDialog() } }
        btnConvertFormat.setOnClickListener { requireVideo { showConvertDialog() } }
    }

    private fun requireVideo(action: () -> Unit) {
        if (inputVideoPath == null) {
            Toast.makeText(this, "Pilih video dulu!", Toast.LENGTH_SHORT).show()
            pickVideo()
        } else {
            action()
        }
    }

    private fun pickVideo() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "video/*"
        }
        startActivityForResult(intent, PICK_VIDEO)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_VIDEO && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            // Copy ke cache dulu
            val inputFile = File(cacheDir, "input_${System.currentTimeMillis()}.mp4")
            try {
                contentResolver.openInputStream(uri)?.use { input ->
                    inputFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                inputVideoPath = inputFile.absolutePath
                tvSelectedVideo.text = "📁 Video: ${inputFile.name}"
                Toast.makeText(this, "Video dipilih", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Gagal load: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showTrimDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_input_two, null)
        val et1 = view.findViewById<EditText>(R.id.etValue1)
        val et2 = view.findViewById<EditText>(R.id.etValue2)
        et1.hint = "Start (detik) - misal 5"
        et2.hint = "Durasi (detik) - misal 10"

        AlertDialog.Builder(this).setTitle("✂️ Trim Video").setView(view)
            .setPositiveButton("Trim") { _, _ ->
                val start = et1.text.toString().ifEmpty { "0" }
                val dur = et2.text.toString().ifEmpty { "10" }
                val out = outputFile("trim")
                runFfmpeg("Trim", arrayOf(
                    "-y", "-i", inputVideoPath!!,
                    "-ss", start, "-t", dur,
                    "-c", "copy", out
                ))
            }
            .setNegativeButton("Batal", null).show()
    }

    private fun showCropDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_input_two, null)
        val et1 = view.findViewById<EditText>(R.id.etValue1)
        val et2 = view.findViewById<EditText>(R.id.etValue2)
        et1.hint = "Width px (misal 720)"
        et2.hint = "Height px (misal 1280)"

        AlertDialog.Builder(this).setTitle("🖼️ Crop Video").setView(view)
            .setPositiveButton("Crop") { _, _ ->
                val w = et1.text.toString().ifEmpty { "720" }
                val h = et2.text.toString().ifEmpty { "1280" }
                val out = outputFile("crop")
                runFfmpeg("Crop", arrayOf(
                    "-y", "-i", inputVideoPath!!,
                    "-vf", "crop=$w:$h",
                    "-c:a", "copy", out
                ))
            }
            .setNegativeButton("Batal", null).show()
    }

    private fun showRotateDialog() {
        val opts = arrayOf("90° Kanan", "180°", "90° Kiri")
        AlertDialog.Builder(this).setTitle("🔄 Rotate")
            .setItems(opts) { _, which ->
                val transpose = when (which) {
                    0 -> "1"   // clockwise 90
                    1 -> "2"   // 180
                    else -> "2,transpose=2,transpose=2"  // counter-clockwise
                }
                val out = outputFile("rotate")
                runFfmpeg("Rotate", arrayOf(
                    "-y", "-i", inputVideoPath!!,
                    "-vf", "transpose=$transpose",
                    "-c:a", "copy", out
                ))
            }.show()
    }

    private fun showFlipDialog() {
        val opts = arrayOf("Horizontal (kiri-kanan)", "Vertical (atas-bawah)")
        AlertDialog.Builder(this).setTitle("🪞 Flip")
            .setItems(opts) { _, which ->
                val filter = if (which == 0) "hflip" else "vflip"
                val out = outputFile("flip")
                runFfmpeg("Flip", arrayOf(
                    "-y", "-i", inputVideoPath!!,
                    "-vf", filter,
                    "-c:a", "copy", out
                ))
            }.show()
    }

    private fun showSpeedDialog() {
        val opts = arrayOf("0.5x (Slow)", "1.5x", "2x (Fast)", "3x", "4x")
        AlertDialog.Builder(this).setTitle("⚡ Speed Control")
            .setItems(opts) { _, which ->
                val speed = arrayOf("0.5", "1.5", "2", "3", "4")[which]
                val out = outputFile("speed")
                runFfmpeg("Speed", arrayOf(
                    "-y", "-i", inputVideoPath!!,
                    "-filter:v", "setpts=$speed*PTS",
                    "-filter:a", "atempo=$speed",
                    out
                ))
            }.show()
    }

    private fun showVolumeDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_input_one, null)
        val et1 = view.findViewById<EditText>(R.id.etValue)
        et1.hint = "Volume (0.5 - 3.0, misal 1.5)"
        AlertDialog.Builder(this).setTitle("🔊 Volume Control").setView(view)
            .setPositiveButton("OK") { _, _ ->
                val vol = et1.text.toString().ifEmpty { "1.5" }
                val out = outputFile("volume")
                runFfmpeg("Volume", arrayOf(
                    "-y", "-i", inputVideoPath!!,
                    "-filter:a", "volume=$vol",
                    "-c:v", "copy", out
                ))
            }
            .setNegativeButton("Batal", null).show()
    }

    private fun showFilterDialog() {
        val opts = arrayOf("Hitam Putih", "Vintage", "Sepia", "Negative", "Blur", "Cerah")
        AlertDialog.Builder(this).setTitle("🎨 Video Filter")
            .setItems(opts) { _, which ->
                val filter = when (which) {
                    0 -> "hue=s=0"
                    1 -> "curves=vintage"
                    2 -> "colorchannelmixer=.393:.769:.189:0:.349:.686:.168:0:.272:.534:.131"
                    3 -> "negate"
                    4 -> "boxblur=5:1"
                    else -> "eq=brightness=0.1:contrast=1.2"
                }
                val out = outputFile("filter")
                runFfmpeg("Filter", arrayOf(
                    "-y", "-i", inputVideoPath!!,
                    "-vf", filter, out
                ))
            }.show()
    }

    private fun showExtractAudioDialog() {
        AlertDialog.Builder(this).setTitle("🎵 Extract Audio")
            .setMessage("Ambil audio dari video jadi MP3?")
            .setPositiveButton("Ya") { _, _ ->
                val out = outputFile("audio", "mp3")
                runFfmpeg("Extract Audio", arrayOf(
                    "-y", "-i", inputVideoPath!!,
                    "-vn", "-acodec", "mp3", out
                ))
            }.setNegativeButton("Batal", null).show()
    }

    private fun showCompressDialog() {
        val opts = arrayOf("Low (360p)", "Medium (720p)", "High (1080p)")
        AlertDialog.Builder(this).setTitle("📦 Compress Video")
            .setItems(opts) { _, which ->
                val crf = arrayOf("32", "28", "23")[which]
                val out = outputFile("compressed")
                runFfmpeg("Compress", arrayOf(
                    "-y", "-i", inputVideoPath!!,
                    "-c:v", "libx264", "-crf", crf, "-preset", "fast",
                    "-c:a", "aac", "-b:a", "128k", out
                ))
            }.show()
    }

    private fun showConvertDialog() {
        val opts = arrayOf("MP4", "MKV", "AVI", "GIF")
        AlertDialog.Builder(this).setTitle("🔄 Convert Format")
            .setItems(opts) { _, which ->
                val ext = arrayOf("mp4", "mkv", "avi", "gif")[which]
                val out = outputFile("converted", ext)
                val cmd = if (ext == "gif") {
                    arrayOf("-y", "-i", inputVideoPath!!, "-vf", "fps=10,scale=480:-1", out)
                } else {
                    arrayOf("-y", "-i", inputVideoPath!!, out)
                }
                runFfmpeg("Convert to $ext", cmd)
            }.show()
    }

    private fun outputFile(prefix: String, ext: String = "mp4"): String {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "VideoEditor")
        if (!dir.exists()) dir.mkdirs()
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return File(dir, "${prefix}_$timestamp.$ext").absolutePath
    }

    private fun runFfmpeg(title: String, cmd: Array<String>) {
        if (!ffmpeg.isSupported) {
            Toast.makeText(this, "FFmpeg tidak support", Toast.LENGTH_LONG).show()
            return
        }

        val pd = ProgressDialog(this).apply {
            setTitle(title)
            setMessage("Processing...")
            setCancelable(false)
            show()
        }

        try {
            ffmpeg.execute(cmd, object : ExecuteBinaryResponseHandler() {
                override fun onSuccess(message: String?) {
                    runOnUiThread {
                        pd.dismiss()
                        Toast.makeText(this@VideoEditorActivity,
                            "✅ Selesai! Cek folder Movies/VideoEditor", Toast.LENGTH_LONG).show()
                    }
                }

                override fun onFailure(message: String?) {
                    runOnUiThread {
                        pd.dismiss()
                        AlertDialog.Builder(this@VideoEditorActivity)
                            .setTitle("❌ Gagal")
                            .setMessage("Error: ${message?.take(200)}")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }

                override fun onProgress(message: String?) {
                    Log.d(TAG, "FFmpeg: $message")
                }

                override fun onFinish() {}
            })
        } catch (e: Exception) {
            pd.dismiss()
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
