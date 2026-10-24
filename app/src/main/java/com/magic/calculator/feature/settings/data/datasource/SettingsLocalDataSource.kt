package com.magic.calculator.feature.settings.data.datasource

import android.content.Context
import android.content.SharedPreferences
import com.magic.calculator.feature.settings.domain.model.MagicSettings
import com.magic.calculator.core.logic.AuthManager

interface SettingsLocalDataSource {
    fun getSettings(): MagicSettings
    fun setShakeSensitivity(value: Float)
    fun setSoundVolume(value: Int)
    fun setHapticIntensity(value: Int)

    // Extended settings
    var activeMagic: String
    var forcedPhoneNumber: String
    var forcedLuckyNumber: String
    var customFlipTrigger: String
    var customFlipResult: String
    var secretUnlockCode: String
    var appMode: Int
    var userPhoneNumber: String
    var isDialerThemeActive: Boolean
    var explosionTrigger: String
    var isExplosionEnabled: Boolean
    var activeSensorTrigger: String
    var toxicMode: Int
    var ghostTypingNumber: String
    var ghostTypingDelay: Int
    var forcedGalleryTime: String
    var forcedContactName: String
    var forcedContactPhone: String
    var isContactForceEnabled: Boolean
    var isP2PEnabled: Boolean
    var p2pSessionId: String
    var p2pPartnerMagicId: String
    var isP2PMirrorEnabled: Boolean
    var isGalleryThemeDark: Boolean
    var isDialerThemeDark: Boolean
    var stepRevealSequence: String

    // Wallpaper & Voice Magic Settings
    var isWallpaperMagicEnabled: Boolean
    var wallpaperMagicTarget: Int
    var forcedWallpaperNumber: String
    var isVoiceListenerEnabled: Boolean
    var wallpaperStyleIndex: Int
    var wallpaperTriggerMethod: String
    var wallpaperContentType: String
    var forcedWallpaperCard: String
}

class SharedPreferencesSettingsLocalDataSource(
    private val context: Context
) : SettingsLocalDataSource {

    private val prefs: SharedPreferences
        get() {
            val uid = AuthManager.currentUid ?: "guest"
            return context.getSharedPreferences("magic_calculator_settings_$uid", Context.MODE_PRIVATE)
        }

    private val globalPrefs: SharedPreferences
        get() = context.getSharedPreferences("magic_calculator_global_settings", Context.MODE_PRIVATE)

    companion object {
        const val DEFAULT_SENSITIVITY = 15f
        const val DEFAULT_VOLUME = 80
        const val DEFAULT_HAPTIC = 80
    }

    override fun getSettings(): MagicSettings {
        return MagicSettings(
            shakeSensitivity = prefs.getFloat("shake_sensitivity", DEFAULT_SENSITIVITY),
            soundVolume = prefs.getInt("sound_volume", DEFAULT_VOLUME),
            hapticIntensity = prefs.getInt("haptic_intensity", DEFAULT_HAPTIC)
        )
    }

    override fun setShakeSensitivity(value: Float) {
        prefs.edit().putFloat("shake_sensitivity", value).apply()
    }

    override fun setSoundVolume(value: Int) {
        prefs.edit().putInt("sound_volume", value).apply()
    }

    override fun setHapticIntensity(value: Int) {
        prefs.edit().putInt("haptic_intensity", value).apply()
    }

    override var activeMagic: String
        get() = prefs.getString("active_magic", "NONE") ?: "NONE"
        set(value) = prefs.edit().putString("active_magic", value).apply()

    override var forcedPhoneNumber: String
        get() = prefs.getString("forced_phone", "8815114272") ?: "8815114272"
        set(value) = prefs.edit().putString("forced_phone", value).apply()

    override var forcedLuckyNumber: String
        get() = prefs.getString("forced_lucky", "777") ?: "777"
        set(value) = prefs.edit().putString("forced_lucky", value).apply()

    override var customFlipTrigger: String
        get() = prefs.getString("flip_trigger", "66") ?: "66"
        set(value) = prefs.edit().putString("flip_trigger", value).apply()

    override var customFlipResult: String
        get() = prefs.getString("flip_result", "99") ?: "99"
        set(value) = prefs.edit().putString("flip_result", value).apply()

    override var secretUnlockCode: String
        get() {
            val code = prefs.getString("secret_unlock_code", null)
                ?: globalPrefs.getString("secret_unlock_code", "741")
            return if (code.isNullOrBlank()) "741" else code.trim()
        }
        set(value) {
            val trimmed = value.trim()
            prefs.edit().putString("secret_unlock_code", trimmed).apply()
            globalPrefs.edit().putString("secret_unlock_code", trimmed).apply()
        }

    override var appMode: Int
        get() = prefs.getInt("app_mode", 0)
        set(value) = prefs.edit().putInt("app_mode", value).apply()

    override var userPhoneNumber: String
        get() = prefs.getString("user_phone_number", "") ?: ""
        set(value) = prefs.edit().putString("user_phone_number", value.trim()).apply()

    override var isDialerThemeActive: Boolean
        get() = appMode == 1
        set(value) {
            if (value) appMode = 1 else if (appMode == 1) appMode = 0
        }

    override var explosionTrigger: String
        get() = prefs.getString("explosion_trigger", "PROXIMITY") ?: "PROXIMITY"
        set(value) = prefs.edit().putString("explosion_trigger", value).apply()

    override var isExplosionEnabled: Boolean
        get() = prefs.getBoolean("explosion_enabled", false)
        set(value) = prefs.edit().putBoolean("explosion_enabled", value).apply()

    override var activeSensorTrigger: String
        get() = prefs.getString("sensor_trigger", "NONE") ?: "NONE"
        set(value) = prefs.edit().putString("sensor_trigger", value).apply()

    override var toxicMode: Int
        get() = prefs.getInt("toxic_mode", 0)
        set(value) = prefs.edit().putInt("toxic_mode", value).apply()

    override var ghostTypingNumber: String
        get() = prefs.getString("ghost_typing_number", "1234") ?: "1234"
        set(value) = prefs.edit().putString("ghost_typing_number", value).apply()

    override var ghostTypingDelay: Int
        get() = prefs.getInt("ghost_typing_delay", 5)
        set(value) = prefs.edit().putInt("ghost_typing_delay", value).apply()

    override var forcedGalleryTime: String
        get() = prefs.getString("forced_gallery_time", "04:20") ?: "04:20"
        set(value) = prefs.edit().putString("forced_gallery_time", value).apply()

    override var forcedContactName: String
        get() = prefs.getString("forced_contact_name", "Rohit Sharma") ?: "Rohit Sharma"
        set(value) = prefs.edit().putString("forced_contact_name", value).apply()

    override var forcedContactPhone: String
        get() = prefs.getString("forced_contact_phone", "+918815114272") ?: "+918815114272"
        set(value) = prefs.edit().putString("forced_contact_phone", value).apply()

    override var isContactForceEnabled: Boolean
        get() = prefs.getBoolean("contact_force_enabled", false)
        set(value) = prefs.edit().putBoolean("contact_force_enabled", value).apply()

    override var isP2PEnabled: Boolean
        get() = prefs.getBoolean("p2p_enabled", false)
        set(value) = prefs.edit().putBoolean("p2p_enabled", value).apply()

    override var p2pSessionId: String
        get() = prefs.getString("p2p_session_id", "") ?: ""
        set(value) = prefs.edit().putString("p2p_session_id", value).apply()

    override var p2pPartnerMagicId: String
        get() = prefs.getString("p2p_partner_magic_id", "") ?: ""
        set(value) = prefs.edit().putString("p2p_partner_magic_id", value).apply()

    override var isP2PMirrorEnabled: Boolean
        get() = prefs.getBoolean("p2p_mirror_enabled", false)
        set(value) = prefs.edit().putBoolean("p2p_mirror_enabled", value).apply()

    override var isGalleryThemeDark: Boolean
        get() = prefs.getBoolean("gallery_theme_dark", true)
        set(value) = prefs.edit().putBoolean("gallery_theme_dark", value).apply()

    override var isDialerThemeDark: Boolean
        get() = prefs.getBoolean("dialer_theme_dark", false)
        set(value) = prefs.edit().putBoolean("dialer_theme_dark", value).apply()

    override var stepRevealSequence: String
        get() = prefs.getString("step_reveal_sequence", "TOUCH,PROXIMITY,FACE_DOWN,LIGHT,TOUCH") ?: "TOUCH,PROXIMITY,FACE_DOWN,LIGHT,TOUCH"
        set(value) = prefs.edit().putString("step_reveal_sequence", value).apply()

    // Wallpaper & Voice Magic Settings
    override var isWallpaperMagicEnabled: Boolean
        get() = prefs.getBoolean("wallpaper_magic_enabled", false)
        set(value) = prefs.edit().putBoolean("wallpaper_magic_enabled", value).apply()

    override var wallpaperMagicTarget: Int
        get() = prefs.getInt("wallpaper_magic_target", 0)
        set(value) = prefs.edit().putInt("wallpaper_magic_target", value).apply()

    override var forcedWallpaperNumber: String
        get() = prefs.getString("forced_wallpaper_number", "74") ?: "74"
        set(value) = prefs.edit().putString("forced_wallpaper_number", value).apply()

    override var isVoiceListenerEnabled: Boolean
        get() = prefs.getBoolean("voice_listener_enabled", false)
        set(value) = prefs.edit().putBoolean("voice_listener_enabled", value).apply()

    override var wallpaperStyleIndex: Int
        get() = prefs.getInt("wallpaper_style_index", 0)
        set(value) = prefs.edit().putInt("wallpaper_style_index", value).apply()

    override var wallpaperTriggerMethod: String
        get() = prefs.getString("wallpaper_trigger_method", "VOL_HOLD") ?: "VOL_HOLD"
        set(value) = prefs.edit().putString("wallpaper_trigger_method", value).apply()

    override var wallpaperContentType: String
        get() = prefs.getString("wallpaper_content_type", "NUMBER") ?: "NUMBER"
        set(value) = prefs.edit().putString("wallpaper_content_type", value).apply()

    override var forcedWallpaperCard: String
        get() = prefs.getString("forced_wallpaper_card", "AS") ?: "AS"
        set(value) = prefs.edit().putString("forced_wallpaper_card", value).apply()
}
