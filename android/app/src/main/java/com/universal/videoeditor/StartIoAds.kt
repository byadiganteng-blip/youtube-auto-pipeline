package com.universal.videoeditor

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.FrameLayout
import com.ironsource.mediationsdk.IronSource
import com.ironsource.mediationsdk.logger.IronSourceError
import com.ironsource.mediationsdk.sdk.InterstitialListener
import com.ironsource.mediationsdk.sdk.RewardedVideoListener

/**
 * StartIoAds — Start.io 8.11.1 wrapper
 * Fitur: Interstitial + Rewarded (Banner di-skip karena API berubah).
 */
object StartIoAds {
    private const val TAG = "StartIoAds"
    private const val PREF = "ads_state"
    private const val KEY_FAIL_COUNT = "fail_count"
    private const val KEY_SKIP_UNTIL = "skip_until"

    private const val START_INTERSTITIAL_TIMEOUT = 5000L
    private const val REWARDED_TIMEOUT = 8000L
    private const val FAIL_THRESHOLD = 3
    private const val SKIP_DURATION = 60 * 60 * 1000L

    @Volatile private var initialized = false
    @Volatile var interstitialReady = false
    @Volatile var rewardedReady = false

    private val handler = Handler(Looper.getMainLooper())

    var onInterstitialClosed: (() -> Unit)? = null
    var onRewardedEarned: (() -> Unit)? = null
    var onRewardedClosed: (() -> Unit)? = null
    var onRewardedFailed: ((String) -> Unit)? = null

    private fun isSkipped(c: Context): Boolean = try {
        val skipUntil = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getLong(KEY_SKIP_UNTIL, 0L)
        if (System.currentTimeMillis() < skipUntil) {
            LogTracker.w(c, TAG, "Ads SKIPPED (${(skipUntil - System.currentTimeMillis())/1000}s left)")
            true
        } else false
    } catch (_: Exception) { false }

    private fun recordFailure(c: Context, reason: String) {
        try {
            val prefs = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            val count = prefs.getInt(KEY_FAIL_COUNT, 0) + 1
            prefs.edit().putInt(KEY_FAIL_COUNT, count).apply()
            LogTracker.w(c, TAG, "Failure #$count — $reason")
            if (count >= FAIL_THRESHOLD) {
                prefs.edit()
                    .putLong(KEY_SKIP_UNTIL, System.currentTimeMillis() + SKIP_DURATION)
                    .putInt(KEY_FAIL_COUNT, 0)
                    .apply()
                LogTracker.w(c, TAG, "Ads SKIPPED for 1 hour")
            }
        } catch (_: Exception) {}
    }

    private fun recordSuccess(c: Context) {
        try {
            c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                .putInt(KEY_FAIL_COUNT, 0).putLong(KEY_SKIP_UNTIL, 0L).apply()
        } catch (_: Exception) {}
    }

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
                    recordFailure(activity, "interstitial show: ${error?.errorMessage}")
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
                    recordFailure(activity, "rewarded show: ${error?.errorMessage}")
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

    fun requireInterstitialOnStart(activity: Activity, onDone: () -> Unit) {
        if (isSkipped(activity)) { onDone(); return }
        if (!initialized) { onDone(); return }

        var doneCalled = false
        val safeDone = { if (!doneCalled) { doneCalled = true; onDone() } }

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
                LogTracker.w(activity, TAG, "Interstitial not ready — polling")
                try { IronSource.loadInterstitial() } catch (_: Exception) {}
                var attempts = 0
                val poll = object : Runnable {
                    override fun run() {
                        attempts++
                        if (doneCalled) return
                        if (IronSource.isInterstitialReady()) {
                            handler.removeCallbacks(timeout)
                            onInterstitialClosed = {
                                onInterstitialClosed = null
                                recordSuccess(activity)
                                safeDone()
                            }
                            IronSource.showInterstitial()
                        } else if (attempts < 10) {
                            handler.postDelayed(this, 500)
                        } else {
                            handler.removeCallbacks(timeout)
                            recordFailure(activity, "start not ready")
                            safeDone()
                        }
                    }
                }
                handler.postDelayed(poll, 500)
            }
        } catch (e: Exception) {
            handler.removeCallbacks(timeout)
            recordFailure(activity, "exception: ${e.message}")
            safeDone()
        }
    }

    fun requireRewarded(activity: Activity,
                        onEarned: () -> Unit,
                        onFailed: (String) -> Unit) {
        if (isSkipped(activity)) { onFailed("ADS_SKIPPED"); return }
        if (!initialized) { onFailed("SDK not init"); return }

        var doneCalled = false
        val safeFail = { r: String -> if (!doneCalled) { doneCalled = true; onFailed(r) } }
        val safeEarn = { if (!doneCalled) { doneCalled = true; onEarned() } }

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
                    onRewardedEarned = null; onRewardedClosed = null; onRewardedFailed = null
                    recordSuccess(activity)
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
                    recordFailure(activity, "rewarded: $err")
                    safeFail(err)
                }
                IronSource.showRewardedVideo()
            } else {
                handler.removeCallbacks(timeout)
                recordFailure(activity, "rewarded not available")
                safeFail("NOT_AVAILABLE")
            }
        } catch (e: Exception) {
            handler.removeCallbacks(timeout)
            recordFailure(activity, "exception: ${e.message}")
            safeFail(e.message ?: "unknown")
        }
    }

    // Banner di-skip — tidak dipakai di versi 8.11.1
    fun loadBanner(activity: Activity, container: FrameLayout) {
        LogTracker.i(activity, TAG, "Banner SKIPPED (not in this version)")
    }

    fun onResume(activity: Activity) {
        try { IronSource.onResume(activity) } catch (_: Exception) {}
    }
    fun onPause(activity: Activity) {
        try { IronSource.onPause(activity) } catch (_: Exception) {}
    }
}
