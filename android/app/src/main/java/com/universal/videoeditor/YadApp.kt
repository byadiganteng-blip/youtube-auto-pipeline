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
        const val OWNER = "byadiganteng-blip"
        const val REPO = "youtube-auto-pipeline"
        const val WORKFLOW = "pipeline.yml"
        const val DOWNLOAD_DIR = "YadClipper"
    }
}
