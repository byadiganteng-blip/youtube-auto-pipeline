package com.universal.videoeditor
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
object AdminApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    private fun tk(c: Context) = SecureConfig.token(c) ?: BuildConfig.GH_TOKEN
    data class R(val ok: Boolean, val code: Int, val body: String)
    private suspend fun req(c: Context, m: String, u: String, b: String? = null): R =
        withContext(Dispatchers.IO) {
            try {
                val rb = Request.Builder().url(u)
                    .header("Authorization", "token ${tk(c)}")
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "YadAPK")
                when (m) {
                    "GET" -> rb.get()
                    "POST" -> rb.post((b ?: "{}").toRequestBody("application/json".toMediaType()))
                    "DELETE" -> rb.delete()
                }
                client.newCall(rb.build()).execute().use {
                    R(it.isSuccessful, it.code, it.body?.string() ?: "")
                }
            } catch (e: Exception) { R(false, -1, e.message ?: "") }
        }
    suspend fun trigger(c: Context, inputs: Map<String, String>): R {
        val url = "https://api.github.com/repos/${YadApp.REPO_OWNER}/${YadApp.REPO_NAME}/actions/workflows/${YadApp.WORKFLOW_FILE}/dispatches"
        val body = JSONObject().apply {
            put("ref","main"); put("inputs", JSONObject(inputs as Map<*, *>))
        }.toString()
        return req(c, "POST", url, body)
    }
    suspend fun listRuns(c: Context): R = req(c,"GET",
        "https://api.github.com/repos/${YadApp.REPO_OWNER}/${YadApp.REPO_NAME}/actions/runs?per_page=20")
    suspend fun listArtifacts(c: Context, id: Long): R = req(c,"GET",
        "https://api.github.com/repos/${YadApp.REPO_OWNER}/${YadApp.REPO_NAME}/actions/runs/$id/artifacts")
    suspend fun listReleases(c: Context): R = req(c,"GET",
        "https://api.github.com/repos/${YadApp.REPO_OWNER}/${YadApp.REPO_NAME}/releases")
    suspend fun downloadArtifact(c: Context, id: Long): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val r = Request.Builder()
                .url("https://api.github.com/repos/${YadApp.REPO_OWNER}/${YadApp.REPO_NAME}/actions/artifacts/$id/zip")
                .header("Authorization","token ${tk(c)}").header("User-Agent","YadAPK").build()
            client.newCall(r).execute().use { it.body?.bytes() }
        } catch (e: Exception) { null }
    }
}
