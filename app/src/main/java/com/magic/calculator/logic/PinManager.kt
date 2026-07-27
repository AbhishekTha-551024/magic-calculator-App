package com.magic.calculator.logic

import android.content.Context
import android.content.SharedPreferences

class PinManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("moilock_prefs", Context.MODE_PRIVATE)

    var secretDigit: String
        get() = prefs.getString("secret_digit", "5") ?: "5"
        set(value) = prefs.edit().putString("secret_digit", value).apply()

    var fakeDate: String
        get() = prefs.getString("fake_date", "March 3, 2024") ?: "March 3, 2024"
        set(value) = prefs.edit().putString("fake_date", value).apply()

    var permanentPin: String?
        get() = prefs.getString("permanent_pin", null)
        set(value) = prefs.edit().putString("permanent_pin", value).apply()

    var isFirstUnlockDone: Boolean
        get() = prefs.getBoolean("is_first_unlock_done", false)
        set(value) = prefs.edit().putBoolean("is_first_unlock_done", value).apply()

    var masterPin: String
        get() = prefs.getString("master_pin", "00000") ?: "00000"
        set(value) = prefs.edit().putString("master_pin", value).apply()

    var customWallpaperPath: String?
        get() = prefs.getString("custom_wallpaper_path", null)
        set(value) = prefs.edit().putString("custom_wallpaper_path", value).apply()

    fun resetLock() {
        permanentPin = null
        isFirstUnlockDone = false
    }
}
