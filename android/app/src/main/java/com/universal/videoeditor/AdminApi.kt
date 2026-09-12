package com.universal.videoeditor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Admin API - GitHub API client
 * Created by KARYADI, Coding by KARYADI
 */
object AdminApi {

    private const val OWNER = "byadiganteng-blip"
    private const val REPO = "youtube-auto-pipeline"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun req(url: String, method: String = "GET", body: String? = null): Request {
        val b = Request.Builder()
            .url(url)
            .header("Authorization", "token ${SecureConfig.getGithubToken()}")
            .header("Accept", "application/vnd.github.v3+json")
            .header("User-Agent", "YadApp")

        when (method.uppercase()) {
            "POST", "PATCH", "PUT" -> b.method(
                method.uppercase(),
                (body ?: "{}").toRequestBody("application/json".toMediaTypeOrNull())
            )
            "DELETE" -> b.delete()
            else -> b.get()
        }
        return b.build()
    }

    suspend fun triggerBuild(workflow: String = "build-apk.yml", ref: String = "main"): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            try {
                val url = "https://api.github.com/repos/$OWNER/$REPO/actions/workflows/$workflow/dispatches"
                val resp = client.newCall(req(url, "POST", """{"ref":"$ref"}""")).execute()
                if (resp.isSuccessful) true to "Build triggered"
                else false to "HTTP ${resp.code}"
            } catch (e: Exception) {
                false to "Error: ${e.message}"
            }
        }

    suspend fun listRuns(limit: Int = 30): Pair<Boolean, List<WorkflowRun>> =
        withContext(Dispatchers.IO) {
            try {
                val url = "https://api.github.com/repos/$OWNER/$REPO/actions/runs?per_page=$limit"
                val resp = client.newCall(req(url)).execute()
                if (!resp.isSuccessful) return@withContext false to emptyList()
                val arr = JSONObject(resp.body?.string() ?: "{}").optJSONArray("workflow_runs")
                val runs = mutableListOf<WorkflowRun>()
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val r = arr.getJSONObject(i)
                        runs.add(WorkflowRun(
                            r.getLong("id"),
                            r.optString("name"),
                            r.optString("status"),
                            r.optString("conclusion"),
                            r.optString("html_url"),
                            r.optInt("run_number"),
                            r.optString("created_at")
                        ))
                    }
                }
                true to runs
            } catch (e: Exception) {
                false to emptyList()
            }
        }

    suspend fun getLatestLogs(workflow: String = "build-apk.yml"): String =
        withContext(Dispatchers.IO) {
            try {
                val url = "https://api.github.com/repos/$OWNER/$REPO/actions/workflows/$workflow/runs?per_page=1"
                val resp = client.newCall(req(url)).execute()
                val arr = JSONObject(resp.body?.string() ?: "{}").optJSONArray("workflow_runs")
                if (arr == null || arr.length() == 0) return@withContext "No runs"
                val run = arr.getJSONObject(0)
                "Run #${run.getLong("id")}: ${run.optString("status")}/${run.optString("conclusion")}"
            } catch (e: Exception) {
                "Error: ${e.message}"
            }
        }

    data class WorkflowRun(
        val id: Long, val name: String, val status: String,
        val conclusion: String, val htmlUrl: String,
        val runNumber: Int, val createdAt: String
    )
}
