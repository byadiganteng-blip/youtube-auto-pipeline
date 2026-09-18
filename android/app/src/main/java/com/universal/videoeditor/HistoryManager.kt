package com.universal.videoeditor

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * HistoryManager — track proses video:
 * - Link yang sudah diproses (untuk deteksi duplikat)
 * - Part yang sudah didownload (untuk resume)
 * - Folder hasil (untuk cleanup)
 */
object HistoryManager {
    private const val TAG = "History"
    private const val FILE = "history.json"

    data class ProcessRecord(
        val urlHash: String,
        val url: String,
        val timestamp: Long,
        val runId: Long,
        val folderName: String,
        val partsDownloaded: List<Int>,
        val totalParts: Int,
        val completed: Boolean,
        val uploadType: String,
        val quality: String,
        val size: String,
        val partDuration: Int
    )

    private fun file(c: Context): File {
        val dir = c.getExternalFilesDir("history") ?: File(c.filesDir, "history")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, FILE)
    }

    private fun hashUrl(url: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(url.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }

    private fun readAll(c: Context): MutableList<ProcessRecord> {
        val f = file(c)
        if (!f.exists()) return mutableListOf()
        return try {
            val arr = JSONArray(f.readText())
            val out = mutableListOf<ProcessRecord>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val partsArr = o.optJSONArray("partsDownloaded") ?: JSONArray()
                val parts = (0 until partsArr.length()).map { partsArr.getInt(it) }
                out.add(ProcessRecord(
                    urlHash = o.optString("urlHash"),
                    url = o.optString("url"),
                    timestamp = o.optLong("timestamp"),
                    runId = o.optLong("runId"),
                    folderName = o.optString("folderName"),
                    partsDownloaded = parts,
                    totalParts = o.optInt("totalParts", 0),
                    completed = o.optBoolean("completed", false),
                    uploadType = o.optString("uploadType", "video"),
                    quality = o.optString("quality", "original"),
                    size = o.optString("size", "original"),
                    partDuration = o.optInt("partDuration", 60)
                ))
            }
            out
        } catch (e: Exception) {
            LogTracker.e(c, TAG, "Read failed: ${e.message}")
            mutableListOf()
        }
    }

    private fun writeAll(c: Context, list: List<ProcessRecord>) {
        try {
            val arr = JSONArray()
            for (rec in list) {
                val o = JSONObject().apply {
                    put("urlHash", rec.urlHash)
                    put("url", rec.url)
                    put("timestamp", rec.timestamp)
                    put("runId", rec.runId)
                    put("folderName", rec.folderName)
                    put("partsDownloaded", JSONArray(rec.partsDownloaded))
                    put("totalParts", rec.totalParts)
                    put("completed", rec.completed)
                    put("uploadType", rec.uploadType)
                    put("quality", rec.quality)
                    put("size", rec.size)
                    put("partDuration", rec.partDuration)
                }
                arr.put(o)
            }
            file(c).writeText(arr.toString(2))
        } catch (e: Exception) {
            LogTracker.e(c, TAG, "Write failed: ${e.message}")
        }
    }

    /**
     * Cek apakah link ini sudah pernah diproses.
     * Return record kalau ada, null kalau baru.
     */
    fun findProcessed(c: Context, url: String): ProcessRecord? {
        val hash = hashUrl(url)
        val all = readAll(c)
        return all.find { it.urlHash == hash && !it.completed }
    }

    /**
     * Cek apakah link ini SELESAI diproses.
     */
    fun findCompleted(c: Context, url: String): ProcessRecord? {
        val hash = hashUrl(url)
        val all = readAll(c)
        return all.find { it.urlHash == hash && it.completed }
    }

    /**
     * Simpan/mulai record baru.
     */
    fun startProcess(c: Context, url: String, runId: Long, folderName: String,
                     uploadType: String, quality: String, size: String, partDuration: Int) {
        val hash = hashUrl(url)
        val all = readAll(c).toMutableList()
        // Hapus record lama dengan hash sama
        all.removeAll { it.urlHash == hash && !it.completed }
        all.add(ProcessRecord(
            urlHash = hash, url = url, timestamp = System.currentTimeMillis(),
            runId = runId, folderName = folderName,
            partsDownloaded = emptyList(), totalParts = 0,
            completed = false, uploadType = uploadType, quality = quality,
            size = size, partDuration = partDuration
        ))
        writeAll(c, all)
        LogTracker.i(c, TAG, "Started: hash=$hash folder=$folderName")
    }

    /**
     * Mark part sebagai sudah didownload.
     */
    fun markPartDownloaded(c: Context, url: String, partNumber: Int) {
        val hash = hashUrl(url)
        val all = readAll(c).toMutableList()
        val idx = all.indexOfFirst { it.urlHash == hash }
        if (idx >= 0) {
            val rec = all[idx]
            val newParts = (rec.partsDownloaded + partNumber).distinct().sorted()
            all[idx] = rec.copy(partsDownloaded = newParts)
            writeAll(c, all)
            LogTracker.i(c, TAG, "Part $partNumber marked (total: ${newParts.size})")
        }
    }

    /**
     * Mark selesai dengan total parts.
     */
    fun markCompleted(c: Context, url: String, totalParts: Int) {
        val hash = hashUrl(url)
        val all = readAll(c).toMutableList()
        val idx = all.indexOfFirst { it.urlHash == hash }
        if (idx >= 0) {
            all[idx] = all[idx].copy(completed = true, totalParts = totalParts)
            writeAll(c, all)
            LogTracker.i(c, TAG, "Completed: $totalParts parts")
        }
    }

    /**
     * Ambil semua part yang sudah didownload untuk link ini.
     */
    fun getDownloadedParts(c: Context, url: String): List<Int> {
        val hash = hashUrl(url)
        val all = readAll(c)
        return all.find { it.urlHash == hash }?.partsDownloaded ?: emptyList()
    }

    /**
     * Ambil semua history (untuk tampilan).
     */
    fun getAllHistory(c: Context): List<ProcessRecord> = readAll(c).sortedByDescending { it.timestamp }

    /**
     * Cleanup history lebih dari 30 hari.
     */
    fun cleanupOld(c: Context) {
        val cutoff = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        val all = readAll(c)
        val filtered = all.filter { it.timestamp > cutoff }
        if (filtered.size != all.size) {
            writeAll(c, filtered)
            LogTracker.i(c, TAG, "Cleaned ${all.size - filtered.size} old records")
        }
    }
}
