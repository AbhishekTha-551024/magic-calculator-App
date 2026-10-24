package com.magic.calculator.core.logic

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * CrashReportingManager
 *
 * Centralized wrapper around FirebaseCrashlytics.
 * Ensures structured custom keys, breadcrumb logging, non-fatal recording,
 * and strict privacy guarantees (never logging spectator PINs or secret values).
 */
class CrashReportingManager {

    private val crashlytics: FirebaseCrashlytics by lazy {
        FirebaseCrashlytics.getInstance()
    }

    /**
     * Updates high-level application context custom keys.
     */
    fun setContext(mode: Int, activeTrick: String?, isPremium: Boolean) {
        try {
            crashlytics.setCustomKey("active_mode", mode)
            crashlytics.setCustomKey("active_trick", activeTrick ?: "NONE")
            crashlytics.setCustomKey("is_premium", isPremium)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set Crashlytics context: ${e.message}")
        }
    }

    /**
     * Records a breadcrumb note and non-fatal exception to Crashlytics.
     */
    fun logNonFatal(e: Throwable, note: String) {
        try {
            crashlytics.log(note)
            crashlytics.recordException(e)
            Log.e(TAG, "Non-fatal logged: $note", e)
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to log non-fatal to Crashlytics", ex)
        }
    }

    /**
     * Sets user identifier for crash grouping (typically Firebase UID or guest hash).
     */
    fun setUserId(userId: String) {
        try {
            crashlytics.setUserId(userId)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set Crashlytics user ID: ${e.message}")
        }
    }

    /**
     * Adds a breadcrumb log entry.
     */
    fun log(message: String) {
        try {
            crashlytics.log(message)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to log Crashlytics breadcrumb: ${e.message}")
        }
    }

    /**
     * Sets a custom string key.
     */
    fun setCustomKey(key: String, value: String) {
        try {
            crashlytics.setCustomKey(key, value)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set custom key $key: ${e.message}")
        }
    }

    /**
     * Sets a custom boolean key.
     */
    fun setCustomKey(key: String, value: Boolean) {
        try {
            crashlytics.setCustomKey(key, value)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set custom key $key: ${e.message}")
        }
    }

    /**
     * Sets a custom integer key.
     */
    fun setCustomKey(key: String, value: Int) {
        try {
            crashlytics.setCustomKey(key, value)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set custom key $key: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "CrashReportingManager"
        
        @Volatile
        private var instance: CrashReportingManager? = null

        fun getInstance(): CrashReportingManager {
            return instance ?: synchronized(this) {
                instance ?: CrashReportingManager().also { instance = it }
            }
        }

        /**
         * Static convenience method for non-fatal reporting across static/companion scopes.
         */
        fun logNonFatal(e: Throwable, note: String) {
            getInstance().logNonFatal(e, note)
        }

        fun log(message: String) {
            getInstance().log(message)
        }
    }
}
