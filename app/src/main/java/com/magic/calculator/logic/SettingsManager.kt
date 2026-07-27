package com.magic.calculator.logic

import android.content.Context
import android.content.SharedPreferences

/**
 * Persists and manages user preferences for the Magic Calculator.
 * Now supports per-user storage keyed by Firebase UID.
 */
class SettingsManager(context: Context) {

    // Uses Firebase UID as the prefs file name so each account gets its own settings.
    // Falls back to a shared name if not logged in (e.g. first launch).
    private val uid = AuthManager.currentUid ?: "guest"
    private val prefsName = "magic_calculator_settings_$uid"

    private val prefs: SharedPreferences = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    companion object {
        const val DEFAULT_SENSITIVITY = 15f
        const val DEFAULT_VOLUME = 80
        const val DEFAULT_HAPTIC = 80
    }

    var shakeSensitivity: Float
        get() = prefs.getFloat("shake_sensitivity", DEFAULT_SENSITIVITY)
        set(value) = prefs.edit().putFloat("shake_sensitivity", value).apply()

    var soundVolume: Int
        get() = prefs.getInt("sound_volume", DEFAULT_VOLUME)
        set(value) = prefs.edit().putInt("sound_volume", value).apply()

    var hapticIntensity: Int
        get() = prefs.getInt("haptic_intensity", DEFAULT_HAPTIC)
        set(value) = prefs.edit().putInt("haptic_intensity", value).apply()

    var activeMagic: String
        get() = prefs.getString("active_magic", "NONE") ?: "NONE"
        set(value) = prefs.edit().putString("active_magic", value).apply()

    var forcedPhoneNumber: String
        get() = prefs.getString("forced_phone", "8815114272") ?: "8815114272"
        set(value) = prefs.edit().putString("forced_phone", value).apply()

    var forcedLuckyNumber: String
        get() = prefs.getString("forced_lucky", "777") ?: "777"
        set(value) = prefs.edit().putString("forced_lucky", value).apply()

    var customFlipTrigger: String
        get() = prefs.getString("flip_trigger", "66") ?: "66"
        set(value) = prefs.edit().putString("flip_trigger", value).apply()

    var customFlipResult: String
        get() = prefs.getString("flip_result", "99") ?: "99"
        set(value) = prefs.edit().putString("flip_result", value).apply()

    // App Modes: 0 = Magic Calculator, 1 = Dialer Mode, 2 = Real Calculator
    var appMode: Int
        get() = prefs.getInt("app_mode", 0)
        set(value) = prefs.edit().putInt("app_mode", value).apply()

    var isDialerThemeActive: Boolean
        get() = appMode == 1
        set(value) {
            if (value) appMode = 1 else if (appMode == 1) appMode = 0
        }

    var explosionTrigger: String
        get() = prefs.getString("explosion_trigger", "PROXIMITY") ?: "PROXIMITY"
        set(value) = prefs.edit().putString("explosion_trigger", value).apply()

    var isExplosionEnabled: Boolean
        get() = prefs.getBoolean("explosion_enabled", true)
        set(value) = prefs.edit().putBoolean("explosion_enabled", value).apply()

    var activeSensorTrigger: String
        get() = prefs.getString("sensor_trigger", "NONE") ?: "NONE"
        set(value) = prefs.edit().putString("sensor_trigger", value).apply()

    // Toxic Mode: 0 for Time, 1 for Date, 2 for Battery
    var toxicMode: Int
        get() = prefs.getInt("toxic_mode", 0)
        set(value) = prefs.edit().putInt("toxic_mode", value).apply()

    var ghostTypingNumber: String
        get() = prefs.getString("ghost_typing_number", "1234") ?: "1234"
        set(value) = prefs.edit().putString("ghost_typing_number", value).apply()

    var ghostTypingDelay: Int
        get() = prefs.getInt("ghost_typing_delay", 5)
        set(value) = prefs.edit().putInt("ghost_typing_delay", value).apply()

    var forcedGalleryTime: String
        get() = prefs.getString("forced_gallery_time", "04:20") ?: "04:20"
        set(value) = prefs.edit().putString("forced_gallery_time", value).apply()

    var forcedContactName: String
        get() = prefs.getString("forced_contact_name", "Rohit Sharma") ?: "Rohit Sharma"
        set(value) = prefs.edit().putString("forced_contact_name", value).apply()

    var forcedContactPhone: String
        get() = prefs.getString("forced_contact_phone", "+918815114272") ?: "+918815114272"
        set(value) = prefs.edit().putString("forced_contact_phone", value).apply()

    var isContactForceEnabled: Boolean
        get() = prefs.getBoolean("contact_force_enabled", false)
        set(value) = prefs.edit().putBoolean("contact_force_enabled", value).apply()

    // Online P2P Link settings (replaces legacy Bluetooth)
    var isP2PEnabled: Boolean
        get() = prefs.getBoolean("p2p_enabled", false)
        set(value) = prefs.edit().putBoolean("p2p_enabled", value).apply()

    var p2pSessionId: String
        get() = prefs.getString("p2p_session_id", "") ?: ""
        set(value) = prefs.edit().putString("p2p_session_id", value).apply()

    var p2pPartnerMagicId: String
        get() = prefs.getString("p2p_partner_magic_id", "") ?: ""
        set(value) = prefs.edit().putString("p2p_partner_magic_id", value).apply()

    var isP2PMirrorEnabled: Boolean
        get() = prefs.getBoolean("p2p_mirror_enabled", false)
        set(value) = prefs.edit().putBoolean("p2p_mirror_enabled", value).apply()

    var isGalleryThemeDark: Boolean
        get() = prefs.getBoolean("gallery_theme_dark", true)
        set(value) = prefs.edit().putBoolean("gallery_theme_dark", value).apply()

    var isDialerThemeDark: Boolean
        get() = prefs.getBoolean("dialer_theme_dark", false)
        set(value) = prefs.edit().putBoolean("dialer_theme_dark", value).apply()

    var stepRevealSequence: String
        get() = prefs.getString("step_reveal_sequence", "TOUCH,PROXIMITY,FACE_DOWN,LIGHT,TOUCH") ?: "TOUCH,PROXIMITY,FACE_DOWN,LIGHT,TOUCH"
        set(value) = prefs.edit().putString("step_reveal_sequence", value).apply()
}