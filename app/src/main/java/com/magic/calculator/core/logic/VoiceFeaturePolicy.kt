package com.magic.calculator.core.logic

/**
 * VoiceFeaturePolicy
 *
 * The single gating predicate for the wallpaper / voice-stealth feature.
 *
 * It used to be duplicated verbatim in `MainActivity.onPause` (deciding whether to hand off to
 * `StealthVoiceService`) and in `StealthVoiceService.onStartCommand` (deciding whether to stop
 * itself). The two copies could drift, and drift means the service running for a magic the
 * magician has already switched away from — holding the microphone hostage.
 *
 * The value overload is pure so it can be unit-tested without a `Context`.
 */
object VoiceFeaturePolicy {

    /** The magic that owns the wallpaper + voice behaviour. */
    const val WALLPAPER_MAGIC = "WALLPAPER_MAGIC"

    /**
     * True when the voice/wallpaper feature should be live: either switch is on, or wallpaper magic
     * is the active magic. Any *other* magic active while both switches are off disqualifies it.
     */
    fun shouldRun(isWallpaperMagicEnabled: Boolean, isVoiceListenerEnabled: Boolean, activeMagic: String): Boolean {
        val isWallpaperEnabled = isWallpaperMagicEnabled || isVoiceListenerEnabled
        val otherMagicActive = activeMagic != WALLPAPER_MAGIC && activeMagic != "NONE" && !isWallpaperEnabled
        return isWallpaperEnabled && !otherMagicActive
    }

    fun shouldRun(settings: SettingsManager): Boolean = shouldRun(
        isWallpaperMagicEnabled = settings.isWallpaperMagicEnabled,
        isVoiceListenerEnabled = settings.isVoiceListenerEnabled,
        activeMagic = settings.activeMagic
    )
}
