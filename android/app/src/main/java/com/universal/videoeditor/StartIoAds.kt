package com.universal.videoeditor

import android.app.Activity
import android.util.Log
import android.widget.FrameLayout
import android.view.ViewGroup
import com.ironsource.mediationsdk.IronSource
import com.ironsource.mediationsdk.ISBannerSize
import com.ironsource.mediationsdk.logger.IronSourceError
import com.ironsource.mediationsdk.sdk.BannerListener
import com.ironsource.mediationsdk.sdk.InterstitialListener
import com.ironsource.mediationsdk.sdk.RewardedVideoListener

/**
 * StartIoAds — wrapper Start.io / IronSource.
 * Fitur: banner, interstitial, rewarded video.
 */
object StartIoAds {
    private const val TAG = "StartIoAds"
    private var initialized = false

    // State
    @Volatile var interstitialReady = false
    @Volatile var rewardedReady = false

    // Callbacks
    var onInterstitialClosed: (() -> Unit)? = null
    var onRewardedEarned: (() -> Unit)? = null
    var onRewardedClosed: (() -> Unit)? = null
    var onRewardedFailed: ((String) -> Unit)? = null

    fun init(activity: Activity) {
        if (initialized) return
        try {
            // Interstitial listener
            IronSource.setInterstitialListener(object : InterstitialListener {
                override fun onInterstitialAdReady() {
                    interstitialReady = true
                    LogTracker.i(activity, TAG, "Interstitial ready")
                }
                override fun onInterstitialAdLoadFailed(error: IronSourceError?) {
                    interstitialReady = false
                    LogTracker.w(activity, TAG, "Interstitial failed: ${error?.errorMessage}")
                }
                override fun onInterstitialAdOpened() {}
                override fun onInterstitialAdClosed() {
                    interstitialReady = false
                    // Auto-reload next
                    try { IronSource.loadInterstitial() } catch (_: Exception) {}
                    onInterstitialClosed?.invoke()
                }
                override fun onInterstitialAdShowSucceeded() {}
                override fun onInterstitialAdShowFailed(error: IronSourceError?) {
                    LogTracker.w(activity, TAG, "Interstitial show failed: ${error?.errorMessage}")
                    onInterstitialClosed?.invoke()
                }
                override fun onInterstitialAdClicked() {}
            })

            // Rewarded listener
            IronSource.setRewardedVideoListener(object : RewardedVideoListener {
                override fun onRewardedVideoAdOpened() {}
                override fun onRewardedVideoAdClosed() {
                    onRewardedClosed?.invoke()
                }
                override fun onRewardedVideoAvailabilityChanged(available: Boolean) {
                    rewardedReady = available
                    LogTracker.i(activity, TAG, "Rewarded available: $available")
                }
                override fun onRewardedVideoAdStarted() {}
                override fun onRewardedVideoAdEnded() {}
                override fun onRewardedVideoAdRewarded(placement: com.ironsource.mediationsdk.model.Placement?) {
                    LogTracker.i(activity, TAG, "Rewarded EARNED: ${placement?.placementName}")
                    onRewardedEarned?.invoke()
                }
                override fun onRewardedVideoAdShowFailed(error: IronSourceError?) {
                    LogTracker.w(activity, TAG, "Rewarded show failed: ${error?.errorMessage}")
                    onRewardedFailed?.invoke(error?.errorMessage ?: "unknown")
                }
                override fun onRewardedVideoAdClicked(placement: com.ironsource.mediationsdk.model.Placement?) {}
            })

            IronSource.init(activity, BuildConfig.STARTIO_APP_ID)
            // Pre-load
            IronSource.loadInterstitial()
            initialized = true
            LogTracker.i(activity, TAG, "SDK init OK — App ID: ${BuildConfig.STARTIO_APP_ID}")
        } catch (e: Exception) {
            LogTracker.e(activity, TAG, "Init failed: ${e.message}")
        }
    }

    /**
     * Wajib tonton interstitial saat buka app.
     * onDone dipanggil setelah interstitial closed atau gagal.
     */
    fun requireInterstitialOnStart(activity: Activity, onDone: () -> Unit) {
        try {
            if (!initialized) {
                LogTracker.w(activity, TAG, "SDK not init, skip start ad")
                onDone()
                return
            }
            if (IronSource.isInterstitialReady()) {
                LogTracker.i(activity, TAG, "Showing START interstitial")
                onInterstitialClosed = {
                    onInterstitialClosed = null
                    onDone()
                }
                IronSource.showInterstitial()
            } else {
                LogTracker.w(activity, TAG, "Start interstitial not ready — try load + timeout")
                // Load dan tunggu max 4 detik
                try { IronSource.loadInterstitial() } catch (_: Exception) {}
                activity.window.decorView.postDelayed({
                    if (IronSource.isInterstitialReady()) {
                        onInterstitialClosed = {
                            onInterstitialClosed = null
                            onDone()
                        }
                        IronSource.showInterstitial()
                    } else {
                        LogTracker.w(activity, TAG, "Start interstitial timeout — proceed")
                        onDone()
                    }
                }, 4000)
            }
        } catch (e: Exception) {
            LogTracker.e(activity, TAG, "requireInterstitial err: ${e.message}")
            onDone()
        }
    }

    /**
     * Wajib tonton rewarded sebelum proses video.
     * onEarned dipanggil saat user selesai nonton (dapat reward).
     * onFailed dipanggil kalau iklan tidak bisa ditampilkan.
     */
    fun requireRewarded(activity: Activity,
                        onEarned: () -> Unit,
                        onFailed: (String) -> Unit) {
        try {
            if (!initialized) {
                onFailed("SDK belum siap")
                return
            }
            if (IronSource.isRewardedVideoAvailable()) {
                LogTracker.i(activity, TAG, "Showing REWARDED")
                onRewardedEarned = {
                    onRewardedEarned = null
                    onRewardedClosed = null
                    onRewardedFailed = null
                    onEarned()
                }
                onRewardedClosed = {
                    // Kalau closed tanpa earned, user tidak dapat reward
                    if (onRewardedEarned != null) {
                        LogTracker.w(activity, TAG, "Rewarded closed without reward")
                        onRewardedEarned = null
                        onRewardedClosed = null
                        onRewardedFailed = null
                        onFailed("Video belum selesai ditonton")
                    }
                }
                onRewardedFailed = { err ->
                    onRewardedEarned = null
                    onRewardedClosed = null
                    onRewardedFailed = null
                    onFailed(err)
                }
                IronSource.showRewardedVideo()
            } else {
                LogTracker.w(activity, TAG, "Rewarded not available")
                onFailed("Video belum siap, tunggu sebentar")
            }
        } catch (e: Exception) {
            LogTracker.e(activity, TAG, "requireRewarded err: ${e.message}")
            onFailed(e.message ?: "unknown")
        }
    }

    /**
     * Banner load di container.
     */
    fun loadBanner(activity: Activity, container: FrameLayout) {
        try {
            if (!initialized) return
            val banner = IronSource.createBanner(activity, ISBannerSize.BANNER)
            val params = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            container.addView(banner, 0, params)
            IronSource.loadBanner(banner, "DefaultBanner")

            banner.setBannerListener(object : BannerListener {
                override fun onBannerAdLoaded() {
                    LogTracker.i(activity, TAG, "Banner loaded")
                }
                override fun onBannerAdLoadFailed(error: IronSourceError?) {
                    LogTracker.w(activity, TAG, "Banner failed: ${error?.errorMessage}")
                }
                override fun onBannerAdClicked() {}
                override fun onBannerAdScreenPresented() {}
                override fun onBannerAdScreenDismissed() {}
                override fun onBannerAdLeftApplication() {}
            })
        } catch (e: Exception) {
            LogTracker.e(activity, TAG, "loadBanner err: ${e.message}")
        }
    }

    fun onResume(activity: Activity) {
        try { IronSource.onResume(activity) } catch (_: Exception) {}
    }
    fun onPause(activity: Activity) {
        try { IronSource.onPause(activity) } catch (_: Exception) {}
    }
}
