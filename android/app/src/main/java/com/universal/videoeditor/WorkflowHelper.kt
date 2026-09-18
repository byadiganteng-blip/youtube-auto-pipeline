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
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS).build()

    private fun tk(c: Context) = SecureConfig.token(c) ?: BuildConfig.GH_TOKEN

    data class R(val ok: Boolean, val code: Int, val body: String)

    private suspend fun req(c: Context, m: String, u: String, b: String? = null): R =
        withContext(Dispatchers.IO) {
            try {
                val rb = Request.Builder().url(u)
                    .header("Authorization", "token ${tk(c)}")
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "YadClipper")
                when (m) {
                    "GET" -> rb.get()
                    "POST" -> rb.post((b ?: "{}").toRequestBody("application/json".toMediaType()))
                }
                client.newCall(rb.build()).execute().use {
                    R(it.isSuccessful, it.code, it.body?.string() ?: "")
                }
            } catch (e: Exception) { R(false, -1, e.message ?: "") }
        }

    suspend fun startProcess(c: Context, inputs: Map<String, String>): R {
        val url = "https://api.github.com/repos/${YadApp.OWNER}/${YadApp.REPO}/actions/workflows/${YadApp.WORKFLOW}/dispatches"
        val body = JSONObject().apply {
            put("ref", "main")
            put("inputs", JSONObject(inputs as Map<*, *>))
        }.toString()
        return req(c, "POST", url, body)
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
                    out.add(Triple(
                        a.optString("name"),
                        a.optLong("id"),
                        a.optString("archive_download_url")
                    ))
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
                    .header("User-Agent", "YadClipper").build()
                client.newCall(r).execute().use { it.body?.bytes() }
            } catch (e: Exception) { null }
        }
}
