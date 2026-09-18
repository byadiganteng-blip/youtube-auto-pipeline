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
                    "DELETE" -> rb.delete()
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
            "upload_type"   to (inputs["upload_type"] ?: "video"),
            "privacy"       to (inputs["privacy"] ?: "public"),
            "auto_upload"   to (inputs["auto_upload"] ?: "false"),
            "start_part"    to (inputs["start_part"] ?: "1")
        )
        val body = JSONObject().apply {
            put("ref", "main")
            put("inputs", JSONObject(mapped as Map<*, *>))
        }.toString()
        LogTracker.i(c, TAG, "Dispatch ${mapped.size} inputs")
        return req(c, "POST", url, body)
    }

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
            val sorted = all.sortedWith(compareBy { art ->
                val n = partNumberFromName(art.name)
                if (n > 0) n else 999
            })
            LogTracker.i(c, TAG, "Artifacts: ${sorted.map { it.name }}")
            sorted.filter { it.sizeBytes > 50_000 }
        }

    fun partNumberFromName(name: String): Int {
        val prefix = "part-"
        val idx = name.indexOf(prefix)
        if (idx < 0) return 0
        val start = idx + prefix.length
        if (start + 2 > name.length) return 0
        val digits = name.substring(start, start + 2)
        for (ch in digits) {
            if (ch < '0' || ch > '9') return 0
        }
        return digits.toIntOrNull() ?: 0
    }

    suspend fun deleteArtifact(c: Context, artifactId: Long): Boolean =
        withContext(Dispatchers.IO) {
            LogTracker.i(c, TAG, "Auto-delete artifact $artifactId")
            val r = req(c, "DELETE",
                "https://api.github.com/repos/${YadApp.OWNER}/${YadApp.REPO}/actions/artifacts/$artifactId")
            if (r.ok || r.code == 204) {
                LogTracker.i(c, TAG, "Artifact $artifactId deleted")
                true
            } else {
                LogTracker.w(c, TAG, "Delete failed: HTTP ${r.code}")
                false
            }
        }

    suspend fun cleanupRunArtifacts(c: Context, runId: Long): Int =
        withContext(Dispatchers.IO) {
            val arts = runArtifacts(c, runId)
            var deleted = 0
            for (art in arts) {
                if (deleteArtifact(c, art.id)) deleted++
            }
            LogTracker.i(c, TAG, "Cleanup run $runId: $deleted artifacts deleted")
            deleted
        }

    suspend fun probeVideoDuration(c: Context, videoUrl: String): Int? =
        withContext(Dispatchers.IO) {
            try {
                if (!videoUrl.contains("youtube.com") && !videoUrl.contains("youtu.be")) {
                    return@withContext null
                }
                val htmlReq = Request.Builder()
                    .url(videoUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .build()
                probeClient.newCall(htmlReq).execute().use { resp ->
                    val html = resp.body?.string() ?: ""

                    val idx = html.indexOf("lengthSeconds")
                    if (idx >= 0) {
                        var i = idx + "lengthSeconds".length
                        var start = -1
                        while (i < html.length && i < idx + 50) {
                            val c = html[i]
                            if (c in '0'..'9') { start = i; break }
                            if (c == '}') break
                            i++
                        }
                        if (start >= 0) {
                            var end = start
                            while (end < html.length && html[end] in '0'..'9') end++
                            val num = html.substring(start, end).toIntOrNull()
                            if (num != null && num > 0) return@withContext num
                        }
                    }

                    val idx2 = html.indexOf("approxDurationMs")
                    if (idx2 >= 0) {
                        var i = idx2 + "approxDurationMs".length
                        var start = -1
                        while (i < html.length && i < idx2 + 50) {
                            val c = html[i]
                            if (c in '0'..'9') { start = i; break }
                            if (c == '}') break
                            i++
                        }
                        if (start >= 0) {
                            var end = start
                            while (end < html.length && html[end] in '0'..'9') end++
                            val ms = html.substring(start, end).toIntOrNull()
                            if (ms != null && ms > 0) return@withContext ms / 1000
                        }
                    }

                    val ptIdx = html.indexOf("PT")
                    if (ptIdx >= 0 && ptIdx < html.length - 5) {
                        var i = ptIdx + 2
                        var minutes = 0
                        var seconds = 0
                        var numStart = i
                        while (i < html.length && html[i] in '0'..'9') i++
                        if (i > numStart) minutes = html.substring(numStart, i).toIntOrNull() ?: 0
                        if (i < html.length && html[i] == 'M') {
                            i++
                            numStart = i
                            while (i < html.length && html[i] in '0'..'9') i++
                            if (i > numStart) seconds = html.substring(numStart, i).toIntOrNull() ?: 0
                        }
                        val total = minutes * 60 + seconds
                        if (total > 0 && total < 24 * 3600) return@withContext total
                    }
                }
                null
            } catch (e: Exception) {
                LogTracker.e(c, TAG, "Probe failed: ${e.message}")
                null
            }
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
