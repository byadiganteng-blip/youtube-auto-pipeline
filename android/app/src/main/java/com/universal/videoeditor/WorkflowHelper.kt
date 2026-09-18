package com.universal.videoeditor

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object WorkflowHelper {
    private const val TAG = "Workflow"
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS).build()

    private val probeClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true).build()

    private fun tk(c: Context) = SecureConfig.token(c) ?: BuildConfig.GH_TOKEN
    data class R(val ok: Boolean, val code: Int, val body: String)

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
                    LogTracker.d(c, TAG, "$m ${u.takeLast(60)} → ${it.code}")
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
        LogTracker.i(c, TAG, "Dispatch with ${mapped.size} inputs")
        return req(c, "POST", url, body)
    }

    /**
     * Probe durasi video dari URL publik.
     * Pakai Raw String Kotlin ("""...""") — WAJIB supaya backslash tidak di-escape.
     */
    suspend fun probeVideoDuration(c: Context, videoUrl: String): Int? =
        withContext(Dispatchers.IO) {
            try {
                if (videoUrl.contains("youtube.com") || videoUrl.contains("youtu.be")) {
                    val htmlReq = Request.Builder()
                        .url(videoUrl)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                        .header("Accept-Language", "en-US,en;q=0.9")
                        .build()
                    probeClient.newCall(htmlReq).execute().use { resp ->
                        val html = resp.body?.string() ?: ""
                        // ⚠️ WAJIB pakai raw string untuk regex dengan \d, " dll
                        val patterns = listOf(
                            Regex(""""lengthSeconds":"(\d+)""""),
                            Regex("""approxDurationMs":"(\d+)""""),
                            Regex("""itemprop="duration" content="PT(\d+)M(\d+)S""""),
                            Regex("""PT(\d+)M(\d+)S""")
                        )
                        for (pat in patterns) {
                            val m = pat.find(html) ?: continue
                            when {
                                pat.pattern.contains("lengthSeconds") -> {
                                    val s = m.groupValues.getOrNull(1)?.toIntOrNull()
                                    if (s != null && s > 0) return@withContext s
                                }
                                pat.pattern.contains("approxDurationMs") -> {
                                    val ms = m.groupValues.getOrNull(1)?.toIntOrNull()
                                    if (ms != null && ms > 0) return@withContext ms / 1000
                                }
                                pat.pattern.contains("itemprop") || pat.pattern == "PT(\d+)M(\d+)S" -> {
                                    val min = m.groupValues.getOrNull(1)?.toIntOrNull() ?: 0
                                    val sec = m.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
                                    val total = min * 60 + sec
                                    if (total > 0) return@withContext total
                                }
                            }
                        }
                    }
                }

                if (videoUrl.contains("tiktok.com")) {
                    val oembed = "https://www.tiktok.com/oembed?url=" + java.net.URLEncoder.encode(videoUrl, "UTF-8")
                    val r = Request.Builder().url(oembed).header("User-Agent", "CliperOn").build()
                    probeClient.newCall(r).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val json = JSONObject(resp.body?.string() ?: "{}")
                            // TikTok oembed tidak punya duration — skip
                        }
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

    suspend fun runArtifacts(c: Context, runId: Long): List<Triple<String, Long, String>> =
        withContext(Dispatchers.IO) {
            val r = req(c, "GET",
                "https://api.github.com/repos/${YadApp.OWNER}/${YadApp.REPO}/actions/runs/$runId/artifacts")
            if (!r.ok) return@withContext emptyList()
            val out = mutableListOf<Triple<String, Long, String>>()
            try {
                val arr = JSONObject(r.body).getJSONArray("artifacts")
                for (i in 0 until arr.length()) {
                    val a = arr.getJSONObject(i)
                    out.add(Triple(a.optString("name"), a.optLong("id"), a.optString("archive_download_url")))
                }
            } catch (_: Exception) {}
            out
        }

    suspend fun downloadArtifact(c: Context, artifactId: Long): ByteArray? =
        withContext(Dispatchers.IO) {
            try {
                val r = Request.Builder()
                    .url("https://api.github.com/repos/${YadApp.OWNER}/${YadApp.REPO}/actions/artifacts/$artifactId/zip")
                    .header("Authorization", "token ${tk(c)}")
                    .header("User-Agent", "CliperOn").build()
                client.newCall(r).execute().use { it.body?.bytes() }
            } catch (e: Exception) { null }
        }
}
