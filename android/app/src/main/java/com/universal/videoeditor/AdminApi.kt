package com.universal.videoeditor

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object AdminApi {
    private const val TAG = "AdminApi"
    private const val OWNER = "byadiganteng-blip"
    private const val REPO  = "youtube-auto-pipeline"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun token(): String = SecureConfig.getGithubToken()

    private fun req(url: String, method: String = "GET", body: String? = null): Request {
        val b = Request.Builder()
            .url(url)
            .header("Authorization", "token ${token()}")
            .header("Accept", "application/vnd.github.v3+json")
            .header("User-Agent", "YadApp")
        when (method.uppercase()) {
            "POST", "PATCH", "PUT" -> b.method(method.uppercase(),
                (body ?: "{}").toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
            "DELETE" -> b.delete()
            else -> b.get()
        }
        return b.build()
    }

    // ---------- PUSH / FORCE UPDATE ----------
    suspend fun triggerBuild(workflow: String = "build-apk.yml", ref: String = "main"): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            try {
                val url = "https://api.github.com/repos/$OWNER/$REPO/actions/workflows/$workflow/dispatches"
                val resp = client.newCall(req(url, "POST", """{"ref":"$ref"}""")).execute()
                if (resp.isSuccessful) true to "Build dipicu (HTTP ${resp.code})"
                else false to "Gagal (HTTP ${resp.code}): ${resp.body?.string()?.take(200)}"
            } catch (e: Exception) {
                Log.e(TAG, "triggerBuild", e); false to "Error: ${e.message}"
            }
        }

    // ---------- GET FILE ----------
    suspend fun getFile(path: String): Pair<String, String>? = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.github.com/repos/$OWNER/$REPO/contents/$path?ref=main"
            val resp = client.newCall(req(url)).execute()
            if (!resp.isSuccessful) return@withContext null
            val json = JSONObject(resp.body!!.string())
            val content = String(
                Base64.decode(json.getString("content").replace("\n", ""), Base64.DEFAULT),
                Charsets.UTF_8
            )
            content to json.getString("sha")
        } catch (e: Exception) { Log.e(TAG, "getFile", e); null }
    }

    // ---------- UPDATE FILE ----------
    suspend fun updateFile(path: String, content: String, msg: String): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            try {
                val existing = getFile(path)
                val sha = existing?.second
                val url = "https://api.github.com/repos/$OWNER/$REPO/contents/$path"
                val body = JSONObject().apply {
                    put("message", msg)
                    put("content", Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
                    if (sha != null) put("sha", sha)
                    put("branch", "main")
                }.toString()
                val resp = client.newCall(req(url, "PUT", body)).execute()
                if (resp.isSuccessful) true to "Tersimpan"
                else false to "Gagal (HTTP ${resp.code}): ${resp.body?.string()?.take(200)}"
            } catch (e: Exception) { false to "Error: ${e.message}" }
        }

    // ---------- VIEW LOGS ----------
    suspend fun getLatestRunLogs(workflow: String = "build-apk.yml"): String =
        withContext(Dispatchers.IO) {
            try {
                val runsUrl = "https://api.github.com/repos/$OWNER/$REPO/actions/workflows/$workflow/runs?per_page=1"
                val runsResp = client.newCall(req(runsUrl)).execute()
                if (!runsResp.isSuccessful) return@withContext "Gagal ambil runs: HTTP ${runsResp.code}"
                val arr = JSONObject(runsResp.body!!.string()).getJSONArray("workflow_runs")
                if (arr.length() == 0) return@withContext "Belum ada workflow run."
                val run = arr.getJSONObject(0)
                val runId = run.getLong("id")
                val sb = StringBuilder()
                sb.append("Run #$runId\n")
                sb.append("Status: ${run.getString("status")} / ${run.optString("conclusion","-")}\n")
                sb.append("Created: ${run.getString("created_at")}\n")
                sb.append("URL: ${run.getString("html_url")}\n\n")

                val jobsUrl = "https://api.github.com/repos/$OWNER/$REPO/actions/runs/$runId/jobs"
                val jobsResp = client.newCall(req(jobsUrl)).execute()
                val jobs = JSONObject(jobsResp.body!!.string()).getJSONArray("jobs")
                for (i in 0 until jobs.length()) {
                    val job = jobs.getJSONObject(i)
                    sb.append("▶ ${job.getString("name")}\n")
                    val steps = job.getJSONArray("steps")
                    for (j in 0 until steps.length()) {
                        val s = steps.getJSONObject(j)
                        val mark = when (s.optString("conclusion")) {
                            "success" -> "✅"
                            "failure" -> "❌"
                            "skipped" -> "⏭️"
                            else -> "⏳"
                        }
                        sb.append("   $mark ${s.getInt("number")}. ${s.getString("name")}\n")
                    }
                    sb.append("\n")
                }
                sb.toString()
            } catch (e: Exception) { "Error: ${e.message}" }
        }

    // ---------- BROADCAST via Gist ----------
    suspend fun broadcast(message: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val gistBody = JSONObject().apply {
                put("description", "YadApp Broadcast ${System.currentTimeMillis()}")
                put("public", false)
                put("files", JSONObject().apply {
                    put("broadcast.json", JSONObject().apply {
                        put("content", """{"msg":"$message","ts":${System.currentTimeMillis()}}""")
                    })
                })
            }.toString()
            val resp = client.newCall(req("https://api.github.com/gists", "POST", gistBody)).execute()
            if (resp.isSuccessful) {
                val id = JSONObject(resp.body!!.string()).getString("id")
                true to "Broadcast terkirim (gist: $id)"
            } else false to "Gagal: HTTP ${resp.code}"
        } catch (e: Exception) { false to "Error: ${e.message}" }
    }

    // ---------- LIST WORKFLOW RUNS ----------
    suspend fun listRuns(workflow: String = "build-apk.yml", limit: Int = 10): List<Triple<Long, String, String>> =
        withContext(Dispatchers.IO) {
            try {
                val url = "https://api.github.com/repos/$OWNER/$REPO/actions/workflows/$workflow/runs?per_page=$limit"
                val resp = client.newCall(req(url)).execute()
                val arr = JSONObject(resp.body!!.string()).getJSONArray("workflow_runs")
                (0 until arr.length()).map {
                    val r = arr.getJSONObject(it)
                    Triple(r.getLong("id"), r.getString("status"), r.optString("conclusion","-"))
                }
            } catch (e: Exception) { emptyList() }
        }
}
