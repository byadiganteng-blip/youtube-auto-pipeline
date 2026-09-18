package com.universal.videoeditor

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.FrameLayout
import com.ironsource.mediationsdk.IronSource
import com.ironsource.mediationsdk.logger.IronSourceError
import com.ironsource.mediationsdk.sdk.LevelPlayInterstitialListener
import com.ironsource.mediationsdk.sdk.LevelPlayRewardedVideoListener
import com.ironsource.mediationsdk.adunit.adapter.utility.AdInfo

/**
 * StartIoAds — INSTANT SKIP version.
 * Kalau iklan tidak ready dalam 1 detik, langsung skip (tidak tunggu).
 */
object StartIoAds {
    private const val TAG = "StartIoAds"
    private const val PREF = "ads_state"

    // INSTANT: 1 detik timeout (bukan 5-8 detik)
    private const val TIMEOUT_MS = 1000L

    @Volatile private var initialized = false
    @Volatile var interstitialReady = false
    @Volatile var rewardedReady = false

    private val handler = Handler(Looper.getMainLooper())

    var onInterstitialClosed: (() -> Unit)? = null
    var onRewardedEarned: (() -> Unit)? = null
    var onRewardedClosed: (() -> Unit)? = null
    var onRewardedFailed: ((String) -> Unit)? = null

    fun init(activity: Activity) {
        if (initialized) return
        try {
            IronSource.setLevelPlayInterstitialListener(object : LevelPlayInterstitialListener {
                override fun onAdReady(adInfo: AdInfo?) {
                    interstitialReady = true
                    LogTracker.i(activity, TAG, "Interstitial ready")
                }
                override fun onAdLoadFailed(error: IronSourceError?) {
                    interstitialReady = false
                }
                override fun onAdOpened(adInfo: AdInfo?) {}
                override fun onAdClosed(adInfo: AdInfo?) {
                    interstitialReady = false
                    try { IronSource.loadInterstitial() } catch (_: Exception) {}
                    onInterstitialClosed?.invoke()
                }
                override fun onAdShowSucceeded(adInfo: AdInfo?) {}
                override fun onAdShowFailed(error: IronSourceError?, adInfo: AdInfo?) {
                    onInterstitialClosed?.invoke()
                }
                override fun onAdClicked(adInfo: AdInfo?) {}
            })

            IronSource.setLevelPlayRewardedVideoListener(object : LevelPlayRewardedVideoListener {
                override fun onAdAvailable(adInfo: AdInfo?) {
                    rewardedReady = true
                    LogTracker.i(activity, TAG, "Rewarded available")
                }
                override fun onAdUnavailable() {
                    rewardedReady = false
                }
                override fun onAdOpened(adInfo: AdInfo?) {}
                override fun onAdClosed(adInfo: AdInfo?) {
                    onRewardedClosed?.invoke()
                }
                override fun onAdRewarded(placement: com.ironsource.mediationsdk.model.Placement?, adInfo: AdInfo?) {
                    onRewardedEarned?.invoke()
                }
                override fun onAdShowFailed(error: IronSourceError?, adInfo: AdInfo?) {
                    onRewardedFailed?.invoke(error?.errorMessage ?: "unknown")
                }
                override fun onAdClicked(placement: com.ironsource.mediationsdk.model.Placement?, adInfo: AdInfo?) {}
            })

            IronSource.init(activity, BuildConfig.STARTIO_APP_ID)
            try { IronSource.loadInterstitial() } catch (_: Exception) {}
            initialized = true
            LogTracker.i(activity, TAG, "SDK init OK")
        } catch (e: Exception) {
            LogTracker.e(activity, TAG, "Init failed: ${e.message}")
        }
    }

    /**
     * INSTANT: Kalau iklan ready, tampil. Kalau tidak, langsung skip dalam 1 detik.
     */
    fun requireInterstitialOnStart(activity: Activity, onDone: () -> Unit) {
        if (!initialized) { onDone(); return }

        var doneCalled = false
        val safeDone = { if (!doneCalled) { doneCalled = true; onDone() } }

        // Instant timeout 1s
        val timeout = Runnable { safeDone() }
        handler.postDelayed(timeout, TIMEOUT_MS)

        try {
            if (IronSource.isInterstitialReady()) {
                onInterstitialClosed = {
                    handler.removeCallbacks(timeout)
                    onInterstitialClosed = null
                    safeDone()
                }
                IronSource.showInterstitial()
            } else {
                // Not ready → skip immediately (timeout akan panggil safeDone)
                LogTracker.i(activity, TAG, "Interstitial not ready, skip in 1s")
            }
        } catch (e: Exception) {
            handler.removeCallbacks(timeout)
            safeDone()
        }
    }

    /**
     * INSTANT: Rewarded dengan 1s timeout.
     */
    fun requireRewarded(activity: Activity,
                        onEarned: () -> Unit,
                        onFailed: (String) -> Unit) {
        if (!initialized) { onFailed("SDK_SKIPPED"); return }

        var doneCalled = false
        val safeFail = { r: String -> if (!doneCalled) { doneCalled = true; onFailed(r) } }
        val safeEarn = { if (!doneCalled) { doneCalled = true; onEarned() } }

        val timeout = Runnable { safeFail("TIMEOUT") }
        handler.postDelayed(timeout, TIMEOUT_MS)

        try {
            if (IronSource.isRewardedVideoAvailable()) {
                onRewardedEarned = {
                    handler.removeCallbacks(timeout)
                    onRewardedEarned = null; onRewardedClosed = null; onRewardedFailed = null
                    safeEarn()
                }
                onRewardedClosed = {
                    if (!doneCalled) {
                        handler.removeCallbacks(timeout)
                        onRewardedEarned = null; onRewardedClosed = null; onRewardedFailed = null
                        safeFail("CLOSED_EARLY")
                    }
                }
                onRewardedFailed = { err ->
                    handler.removeCallbacks(timeout)
                    onRewardedEarned = null; onRewardedClosed = null; onRewardedFailed = null
                    safeFail(err)
                }
                IronSource.showRewardedVideo()
            } else {
                // Not ready → skip after timeout
                LogTracker.i(activity, TAG, "Rewarded not ready, skip in 1s")
            }
        } catch (e: Exception) {
            handler.removeCallbacks(timeout)
            safeFail(e.message ?: "unknown")
        }
    }

    fun loadBanner(activity: Activity, container: FrameLayout) {}

    fun onResume(activity: Activity) {
        try { IronSource.onResume(activity) } catch (_: Exception) {}
    }
    fun onPause(activity: Activity) {
        try { IronSource.onPause(activity) } catch (_: Exception) {}
    }
}
