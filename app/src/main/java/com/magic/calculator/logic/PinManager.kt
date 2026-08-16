package com.magic.calculator.logic

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest

/**
 * PinManager — Stores lock screen PINs securely using SHA-256 hashing.
 */
class PinManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("moilock_prefs", Context.MODE_PRIVATE)

    companion object {
        fun hash(value: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = digest.digest(value.toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }

        fun matches(candidate: String, storedHash: String): Boolean {
            return hash(candidate) == storedHash
        }
    }

    var secretDigit: String
        get() = prefs.getString("secret_digit", "5") ?: "5"
        set(value) = prefs.edit().putString("secret_digit", value).apply()

    var fakeDate: String
        get() = prefs.getString("fake_date", "March 3, 2024") ?: "March 3, 2024"
        set(value) = prefs.edit().putString("fake_date", value).apply()

    var customWallpaperPath: String?
        get() = prefs.getString("custom_wallpaper_path", null)
        set(value) = prefs.edit().putString("custom_wallpaper_path", value).apply()

    var isFirstUnlockDone: Boolean
        get() = prefs.getBoolean("is_first_unlock_done", false)
        set(value) = prefs.edit().putBoolean("is_first_unlock_done", value).apply()

    // ── Permanent PIN ───────────────────────────────────────────────────────
    var permanentPin: String?
        get() = prefs.getString("permanent_pin", null) ?: prefs.getString("permanent_pin_hash", null)
        set(value) {
            if (value != null) {
                prefs.edit().putString("permanent_pin_hash", hash(value)).apply()
            } else {
                prefs.edit().remove("permanent_pin_hash").remove("permanent_pin").apply()
            }
        }

    fun verifyPermanentPin(candidate: String): Boolean {
        val storedHash = prefs.getString("permanent_pin_hash", null)
        if (storedHash != null) return matches(candidate, storedHash)
        val legacy = prefs.getString("permanent_pin", null)
        if (legacy != null) {
            permanentPin = legacy
            return candidate == legacy
        }
        return false
    }

    // ── Master PIN ──────────────────────────────────────────────────────────
    var masterPin: String
        get() = prefs.getString("master_pin", "00000") ?: "00000"
        set(value) {
            prefs.edit().putString("master_pin_hash", hash(value)).putString("master_pin", value).apply()
        }

    fun verifyMasterPin(candidate: String): Boolean {
        val storedHash = prefs.getString("master_pin_hash", null)
        if (storedHash != null) return matches(candidate, storedHash)
        val legacy = prefs.getString("master_pin", "00000") ?: "00000"
        return candidate == legacy
    }

    fun resetLock() {
        prefs.edit()
            .remove("permanent_pin")
            .remove("permanent_pin_hash")
            .putBoolean("is_first_unlock_done", false)
            .apply()
    }
}
