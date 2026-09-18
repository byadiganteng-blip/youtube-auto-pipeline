package com.universal.videoeditor
import android.app.Application
class YadApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (SecureConfig.token(this).isNullOrEmpty() && BuildConfig.GH_TOKEN.isNotEmpty()) {
            SecureConfig.save(this, BuildConfig.GH_TOKEN, BuildConfig.GH_USER)
        }
    }
    companion object {
        val REPO_OWNER get() = BuildConfig.GH_OWNER
        val REPO_NAME get() = BuildConfig.GH_REPO
        val WORKFLOW_FILE get() = BuildConfig.GH_WORKFLOW
    }
}
