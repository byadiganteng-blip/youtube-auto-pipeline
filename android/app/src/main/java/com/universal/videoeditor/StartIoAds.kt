package com.universal.videoeditor

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
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
 * StartIoAds — Start.io / IronSource wrapper
 * - Auto-skip kalau iklan gagal/timeout
 * - Cache failure (skip 1 jam kalau 3x gagal)
 * - Semua callback di main thread
 */
object StartIoAds {
    private const val TAG = "StartIoAds"
    private const val PREF = "ads_state"
    private const val KEY_FAIL_COUNT = "fail_count"
    private const val KEY_LAST_FAIL_TS = "last_fail_ts"
    private const val KEY_SKIP_UNTIL = "skip_until"

    private const val START_INTERSTITIAL_TIMEOUT = 5000L   // 5 detik
    private const val REWARDED_TIMEOUT = 8000L             // 8 detik
    private const val FAIL_THRESHOLD = 3                   // 3x gagal → skip 1 jam
    private const val SKIP_DURATION = 60 * 60 * 1000L      // 1 jam

    @Volatile private var initialized = false
    @Volatile var interstitialReady = false
    @Volatile var rewardedReady = false

    private val handler = Handler(Looper.getMainLooper())
    private var timeoutRunnable: Runnable? = null

    // Callbacks
    var onInterstitialClosed: (() -> Unit)? = null
    var onRewardedEarned: (() -> Unit)? = null
    var onRewardedClosed: (() -> Unit)? = null
    var onRewardedFailed: ((String) -> Unit)? = null

    // ═══════════════════════════════════════════════════════════
    // STATE MANAGEMENT — track gagal berturut-turut
    // ═══════════════════════════════════════════════════════════

    private fun isSkipped(c: Context): Boolean {
        return try {
            val prefs = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            val skipUntil = prefs.getLong(KEY_SKIP_UNTIL, 0L)
            if (System.currentTimeMillis() < skipUntil) {
                LogTracker.w(c, TAG, "Ads SKIPPED until ${(skipUntil - System.currentTimeMillis())/1000}s")
                return true
            }
            false
        } catch (e: Exception) { false }
    }

    private fun recordFailure(c: Context, reason: String) {
        try {
            val prefs = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            val count = prefs.getInt(KEY_FAIL_COUNT, 0) + 1
            prefs.edit()
                .putInt(KEY_FAIL_COUNT, count)
                .putLong(KEY_LAST_FAIL_TS, System.currentTimeMillis())
                .apply()
            LogTracker.w(c, TAG, "Failure #$count — $reason")

            if (count >= FAIL_THRESHOLD) {
                val skipUntil = System.currentTimeMillis() + SKIP_DURATION
                prefs.edit()
                    .putLong(KEY_SKIP_UNTIL, skipUntil)
                    .putInt(KEY_FAIL_COUNT, 0)
                    .apply()
                LogTracker.w(c, TAG, "Ads SKIPPED for 1 hour (too many failures)")
            }
        } catch (_: Exception) {}
    }

    private fun recordSuccess(c: Context) {
        try {
            c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                .putInt(KEY_FAIL_COUNT, 0)
                .putLong(KEY_SKIP_UNTIL, 0L)
                .apply()
        } catch (_: Exception) {}
    }

    // ═══════════════════════════════════════════════════════════
    // INIT
    // ═══════════════════════════════════════════════════════════

    fun init(activity: Activity) {
        if (initialized) return
        try {
            IronSource.setInterstitialListener(object : InterstitialListener {
                override fun onInterstitialAdReady() {
                    interstitialReady = true
                    LogTracker.i(activity, TAG, "Interstitial ready")
                }
                override fun onInterstitialAdLoadFailed(error: IronSourceError?) {
                    interstitialReady = false
                    LogTracker.w(activity, TAG, "Interstitial load failed: ${error?.errorMessage}")
                    recordFailure(activity, "load: ${error?.errorMessage}")
                }
                override fun onInterstitialAdOpened() {}
                override fun onInterstitialAdClosed() {
                    interstitialReady = false
                    try { IronSource.loadInterstitial() } catch (_: Exception) {}
                    onInterstitialClosed?.invoke()
                }
                override fun onInterstitialAdShowSucceeded() {
                    recordSuccess(activity)
                }
                override fun onInterstitialAdShowFailed(error: IronSourceError?) {
                    LogTracker.w(activity, TAG, "Interstitial show failed: ${error?.errorMessage}")
                    recordFailure(activity, "show: ${error?.errorMessage}")
                    onInterstitialClosed?.invoke()
                }
                override fun onInterstitialAdClicked() {}
            })

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
                    LogTracker.i(activity, TAG, "Rewarded EARNED")
                    recordSuccess(activity)
                    onRewardedEarned?.invoke()
                }
                override fun onRewardedVideoAdShowFailed(error: IronSourceError?) {
                    LogTracker.w(activity, TAG, "Rewarded show failed: ${error?.errorMessage}")
                    recordFailure(activity, "rewarded: ${error?.errorMessage}")
                    onRewardedFailed?.invoke(error?.errorMessage ?: "unknown")
                }
                override fun onRewardedVideoAdClicked(placement: com.ironsource.mediationsdk.model.Placement?) {}
            })

            IronSource.init(activity, BuildConfig.STARTIO_APP_ID)
            try { IronSource.loadInterstitial() } catch (_: Exception) {}
            initialized = true
            LogTracker.i(activity, TAG, "SDK init OK — App ID: ${BuildConfig.STARTIO_APP_ID}")
        } catch (e: Exception) {
            LogTracker.e(activity, TAG, "Init failed: ${e.message}")
        }
    }

    // ═══════════════════════════════════════════════════════════
    // START INTERSTITIAL — dengan timeout auto-skip
    // ═══════════════════════════════════════════════════════════

    fun requireInterstitialOnStart(activity: Activity, onDone: () -> Unit) {
        // Cek skip state
        if (isSkipped(activity)) {
            LogTracker.i(activity, TAG, "Start interstitial SKIPPED (cached failure)")
            onDone()
            return
        }
        if (!initialized) {
            LogTracker.w(activity, TAG, "SDK not init — skip")
            onDone()
            return
        }

        var doneCalled = false
        val safeDone = {
            if (!doneCalled) {
                doneCalled = true
                onDone()
            }
        }

        // Timeout — auto skip kalau 5 detik tidak tampil
        val timeout = Runnable {
            LogTracker.w(activity, TAG, "Start interstitial TIMEOUT — auto skip")
            recordFailure(activity, "start timeout")
            safeDone()
        }
        handler.postDelayed(timeout, START_INTERSTITIAL_TIMEOUT)

        try {
            if (IronSource.isInterstitialReady()) {
                LogTracker.i(activity, TAG, "Showing START interstitial")
                onInterstitialClosed = {
                    handler.removeCallbacks(timeout)
                    onInterstitialClosed = null
                    recordSuccess(activity)
                    safeDone()
                }
                IronSource.showInterstitial()
            } else {
                LogTracker.w(activity, TAG, "Interstitial not ready — try load + wait")
                try { IronSource.loadInterstitial() } catch (_: Exception) {}

                // Poll tiap 500ms sampai siap atau timeout
                var attempts = 0
                val poll = object : Runnable {
                    override fun run() {
                        attempts++
                        if (doneCalled) return
                        if (IronSource.isInterstitialReady()) {
                            handler.removeCallbacks(timeout)
                            LogTracker.i(activity, TAG, "Interstitial ready after $attempts polls")
                            onInterstitialClosed = {
                                onInterstitialClosed = null
                                recordSuccess(activity)
                                safeDone()
                            }
                            IronSource.showInterstitial()
                        } else if (attempts < 10) {
                            handler.postDelayed(this, 500)
                        } else {
                            LogTracker.w(activity, TAG, "Interstitial never ready — auto skip")
                            handler.removeCallbacks(timeout)
                            recordFailure(activity, "start not ready")
                            safeDone()
                        }
                    }
                }
                handler.postDelayed(poll, 500)
            }
        } catch (e: Exception) {
            LogTracker.e(activity, TAG, "requireInterstitial err: ${e.message}")
            handler.removeCallbacks(timeout)
            recordFailure(activity, "exception: ${e.message}")
            safeDone()
        }
    }

    // ═══════════════════════════════════════════════════════════
    // REWARDED — dengan timeout auto-skip
    // ═══════════════════════════════════════════════════════════

    fun requireRewarded(activity: Activity,
                        onEarned: () -> Unit,
                        onFailed: (String) -> Unit) {
        // Cek skip state
        if (isSkipped(activity)) {
            LogTracker.i(activity, TAG, "Rewarded SKIPPED (cached failure) — allow process")
            onFailed("ADS_SKIPPED")
            return
        }
        if (!initialized) {
            onFailed("SDK belum siap")
            return
        }

        var doneCalled = false
        val safeFail = { reason: String ->
            if (!doneCalled) {
                doneCalled = true
                onFailed(reason)
            }
        }
        val safeEarn = {
            if (!doneCalled) {
                doneCalled = true
                onEarned()
            }
        }

        // Timeout — auto skip kalau 8 detik tidak tampil
        val timeout = Runnable {
            LogTracker.w(activity, TAG, "Rewarded TIMEOUT — auto skip")
            recordFailure(activity, "rewarded timeout")
            safeFail("ADS_TIMEOUT")
        }
        handler.postDelayed(timeout, REWARDED_TIMEOUT)

        try {
            if (IronSource.isRewardedVideoAvailable()) {
                LogTracker.i(activity, TAG, "Showing REWARDED")
                onRewardedEarned = {
                    handler.removeCallbacks(timeout)
                    onRewardedEarned = null
                    onRewardedClosed = null
                    onRewardedFailed = null
                    recordSuccess(activity)
                    safeEarn()
                }
                onRewardedClosed = {
                    if (!doneCalled) {
                        LogTracker.w(activity, TAG, "Rewarded closed without reward")
                        handler.removeCallbacks(timeout)
                        onRewardedEarned = null
                        onRewardedClosed = null
                        onRewardedFailed = null
                        // User close cepat — tetap skip (bukan gagal)
                        safeFail("CLOSED_EARLY")
                    }
                }
                onRewardedFailed = { err ->
                    handler.removeCallbacks(timeout)
                    onRewardedEarned = null
                    onRewardedClosed = null
                    onRewardedFailed = null
                    recordFailure(activity, "rewarded show: $err")
                    safeFail(err)
                }
                IronSource.showRewardedVideo()
            } else {
                LogTracker.w(activity, TAG, "Rewarded not available")
                handler.removeCallbacks(timeout)
                recordFailure(activity, "rewarded not available")
                safeFail("NOT_AVAILABLE")
            }
        } catch (e: Exception) {
            LogTracker.e(activity, TAG, "requireRewarded err: ${e.message}")
            handler.removeCallbacks(timeout)
            recordFailure(activity, "exception: ${e.message}")
            safeFail(e.message ?: "unknown")
        }
    }

    // ═══════════════════════════════════════════════════════════
    // BANNER
    // ═══════════════════════════════════════════════════════════

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
