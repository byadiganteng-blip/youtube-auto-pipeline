package com.universal.videoeditor

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object ProcessRunner {
    private const val TAG = "ProcessRunner"
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val probeClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true).build()

    private fun tk(c: Context) = SecureConfig.token(c) ?: BuildConfig.GH_TOKEN
    data class R(val ok: Boolean, val code: Int, val body: String)
    data class Artifact(val name: String, val id: Long, val sizeBytes: Long, val url: String)

    private suspend fun req(c: Context, m: String, u: String, b: String? = null): R =
        withContext(Dispatchers.IO) {
            try {
                val rb = Request.Builder().url(u)
                    .header("Authorization", "token ${tk(c)}")
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "CliperOn")
                when (m) {
                    "GET" -> rb.get()
                    "POST" -> rb.post((b ?: "{}").toRequestBody("application/json".toMediaType()))
                }
                client.newCall(rb.build()).execute().use {
                    val body = it.body?.string() ?: ""
                    LogTracker.d(c, TAG, "$m ${u.takeLast(60)} -> ${it.code}")
                    R(it.isSuccessful, it.code, body)
                }
            } catch (e: Exception) {
                LogTracker.e(c, TAG, "Network: ${e.message}")
                R(false, -1, e.message ?: "")
            }
        }

    suspend fun startProcess(c: Context, inputs: Map<String, String>): R {
        val url = "https://api.github.com/repos/${YadApp.OWNER}/${YadApp.REPO}/actions/workflows/${YadApp.WORKFLOW}/dispatches"
        val mapped = mapOf(
            "video_url"     to (inputs["video_url"] ?: ""),
            "process_mode"  to (inputs["process_mode"] ?: "remove_watermark"),
            "method"        to (inputs["method"] ?: "blur"),
            "video_size"    to (inputs["video_size"] ?: "original"),
            "video_quality" to (inputs["video_quality"] ?: "original"),
            "part_duration" to (inputs["part_duration"] ?: "60"),
            "upload_type"   to (inputs["upload_type"] ?: "video")
        )
        val body = JSONObject().apply {
            put("ref", "main")
            put("inputs", JSONObject(mapped as Map<*, *>))
        }.toString()
        LogTracker.i(c, TAG, "Dispatch ${mapped.size} inputs")
        return req(c, "POST", url, body)
    }

    suspend fun probeVideoDuration(c: Context, videoUrl: String): Int? =
        withContext(Dispatchers.IO) {
            try {
                if (videoUrl.contains("youtube.com") || videoUrl.contains("youtu.be")) {
                    val htmlReq = Request.Builder()
                        .url(videoUrl)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                        .build()
                    probeClient.newCall(htmlReq).execute().use { resp ->
                        val html = resp.body?.string() ?: ""
                        Regex("lengthSeconds" + "\"" + ":" + "\"" + "([0-9]+)").find(html)?.let {
                            val s = it.groupValues[1].toIntOrNull()
                            if (s != null && s > 0) return@withContext s
                        }
                        Regex("approxDurationMs" + "\"" + ":" + "\"" + "([0-9]+)").find(html)?.let {
                            val ms = it.groupValues[1].toIntOrNull()
                            if (ms != null && ms > 0) return@withContext ms / 1000
                        }
                        Regex("PT([0-9]+)M([0-9]+)S").find(html)?.let {
                            val min = it.groupValues[1].toIntOrNull() ?: 0
                            val sec = it.groupValues[2].toIntOrNull() ?: 0
                            val total = min * 60 + sec
                            if (total > 0) return@withContext total
                        }
                    }
                }
                null
            } catch (e: Exception) { null }
        }

    suspend fun latestRun(c: Context): JSONObject? = withContext(Dispatchers.IO) {
        val r = req(c, "GET",
            "https://api.github.com/repos/${YadApp.OWNER}/${YadApp.REPO}/actions/workflows/${YadApp.WORKFLOW}/runs?per_page=1")
        if (!r.ok) return@withContext null
        try {
            val arr = JSONObject(r.body).getJSONArray("workflow_runs")
            if (arr.length() == 0) null else arr.getJSONObject(0)
        } catch (e: Exception) { null }
    }

    /**
     * Ambil semua artifact dari run, tapi PRIORITASKAN yang namanya mengandung "cliper" atau "pipeline".
     * Skip artifact "build-log" / "analyzer".
     */
    suspend fun runArtifacts(c: Context, runId: Long): List<Artifact> =
        withContext(Dispatchers.IO) {
            val r = req(c, "GET",
                "https://api.github.com/repos/${YadApp.OWNER}/${YadApp.REPO}/actions/runs/$runId/artifacts")
            if (!r.ok) return@withContext emptyList()
            val all = mutableListOf<Artifact>()
            try {
                val arr = JSONObject(r.body).getJSONArray("artifacts")
                for (i in 0 until arr.length()) {
                    val a = arr.getJSONObject(i)
                    all.add(Artifact(
                        name = a.optString("name"),
                        id = a.optLong("id"),
                        sizeBytes = a.optLong("size_in_bytes"),
                        url = a.optString("archive_download_url")
                    ))
                }
            } catch (_: Exception) {}
            // Sort: prioritize yang punya kata "cliper" atau "pipeline", skip yang "log"
            val prioritized = all.sortedByDescending { art ->
                val n = art.name.lowercase()
                when {
                    n.contains("cliper") -> 100
                    n.contains("pipeline") -> 90
                    n.contains("result") -> 80
                    n.contains("apk") -> 70
                    n.contains("log") -> -50
                    n.contains("analyzer") -> -100
                    else -> 0
                }
            }
            LogTracker.i(c, TAG, "Artifacts found: ${all.map { it.name + " (" + it.sizeBytes/1024 + "KB)" }}")
            prioritized.filter { it.sizeBytes > 50_000 }  // skip artifact < 50KB (log kecil)
        }

    suspend fun downloadArtifact(c: Context, artifactId: Long): ByteArray? =
        withContext(Dispatchers.IO) {
            try {
                val r = Request.Builder()
                    .url("https://api.github.com/repos/${YadApp.OWNER}/${YadApp.REPO}/actions/artifacts/$artifactId/zip")
                    .header("Authorization", "token ${tk(c)}")
                    .header("User-Agent", "CliperOn")
                    .build()
                client.newCall(r).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        LogTracker.e(c, TAG, "Download HTTP ${resp.code}")
                        return@withContext null
                    }
                    resp.body?.bytes()
                }
            } catch (e: Exception) {
                LogTracker.e(c, TAG, "Download failed: ${e.message}")
                null
            }
        }
}
