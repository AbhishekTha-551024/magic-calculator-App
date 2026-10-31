package com.magic.calculator.di

import android.app.Activity
import android.content.Context
import androidx.fragment.app.Fragment
import com.magic.calculator.MagicApplication
import com.magic.calculator.core.audio.SoundManager
import com.magic.calculator.core.haptic.HapticManager
import com.magic.calculator.core.logic.AuthManager
import com.magic.calculator.core.logic.MagicEngine
import com.magic.calculator.core.logic.SettingsManager
import com.magic.calculator.core.logic.SubscriptionManager
import com.magic.calculator.core.logic.strategies.MagicRegistry
import com.magic.calculator.feature.p2p.presentation.P2PLinkManager

/**
 * Dependency Injection Container / Service Locator.
 * Centralizes lifecycle management and singleton injection across the app.
 */
class AppContainer(private val context: Context) {

    val appLockRepository: com.magic.calculator.feature.applock.domain.repository.AppLockRepository by lazy {
        com.magic.calculator.feature.applock.data.repository.AppLockRepositoryImpl(
            com.magic.calculator.feature.applock.data.datasource.SharedPreferencesAppLockLocalDataSource(context)
        )
    }

    val secretRepository: com.magic.calculator.feature.secret.domain.repository.SecretRepository by lazy {
        com.magic.calculator.feature.secret.data.repository.SecretRepositoryImpl(
            com.magic.calculator.feature.secret.data.datasource.SharedPreferencesSecretLocalDataSource(settingsManager)
        )
    }

    val settingsRepository: com.magic.calculator.feature.settings.domain.repository.SettingsRepository by lazy {
        com.magic.calculator.feature.settings.data.repository.SettingsRepositoryImpl(
            com.magic.calculator.feature.settings.data.datasource.SharedPreferencesSettingsLocalDataSource(context)
        )
    }

    val settingsManager: SettingsManager by lazy {
        SettingsManager(context)
    }

    val soundManager: SoundManager by lazy {
        SoundManager(context)
    }

    val hapticManager: HapticManager by lazy {
        HapticManager(context)
    }

    val magicRegistry: MagicRegistry by lazy {
        MagicRegistry.getInstance()
    }

    val magicEngine: MagicEngine by lazy {
        MagicEngine(magicRegistry)
    }

    val p2pRepository: com.magic.calculator.feature.p2p.domain.repository.P2PRepository by lazy {
        com.magic.calculator.feature.p2p.data.repository.P2PRepositoryImpl()
    }

    val p2pLinkManager: P2PLinkManager by lazy {
        P2PLinkManager.getInstance()
    }

    /**
     * Sole owner of [com.magic.calculator.feature.voice_wallpaper.data.voice.StealthVoiceListener].
     * Reached from services too — `Context.appContainer` resolves through `applicationContext`.
     */
    val voiceCaptureManager: com.magic.calculator.feature.voice_wallpaper.data.voice.VoiceCaptureManager by lazy {
        com.magic.calculator.feature.voice_wallpaper.data.voice.VoiceCaptureManager(
            context, settingsManager, hapticManager, soundManager
        )
    }

    val calculatorRepository: com.magic.calculator.feature.calculator.domain.repository.CalculatorRepository by lazy {
        com.magic.calculator.feature.calculator.data.repository.CalculatorRepositoryImpl(magicEngine)
    }

    val vaultRepository: com.magic.calculator.feature.vault.domain.repository.VaultRepository by lazy {
        com.magic.calculator.feature.vault.data.repository.VaultRepositoryImpl(
            com.magic.calculator.feature.vault.data.datasource.AssetVaultLocalDataSource(context, settingsManager)
        )
    }

    val dialerRepository: com.magic.calculator.feature.dialer.domain.repository.DialerRepository by lazy {
        com.magic.calculator.feature.dialer.data.repository.DialerRepositoryImpl(
            com.magic.calculator.feature.dialer.data.datasource.ContentProviderDialerLocalDataSource(context)
        )
    }

    val splashRepository: com.magic.calculator.feature.splash.domain.repository.SplashRepository by lazy {
        com.magic.calculator.feature.splash.data.repository.SplashRepositoryImpl(
            com.magic.calculator.feature.splash.data.datasource.FirebaseAuthSplashLocalDataSource()
        )
    }

    val authRepository: com.magic.calculator.feature.auth.domain.repository.AuthRepository by lazy {
        com.magic.calculator.feature.auth.data.repository.AuthRepositoryImpl()
    }

    val authManager: AuthManager
        get() = AuthManager

    val subscriptionRepository: com.magic.calculator.feature.subscription.domain.repository.SubscriptionRepository by lazy {
        com.magic.calculator.feature.subscription.data.repository.SubscriptionRepositoryImpl(
            com.magic.calculator.feature.subscription.data.datasource.SharedPreferencesSubscriptionLocalDataSource(context)
        )
    }

    val subscriptionManager: SubscriptionManager
        get() = SubscriptionManager

    val crashReportingManager: com.magic.calculator.core.logic.CrashReportingManager by lazy {
        com.magic.calculator.core.logic.CrashReportingManager.getInstance()
    }
}

// ── Convenient Extensions for Android Components ───────────────────────────

val Context.appContainer: AppContainer
    get() = (applicationContext as MagicApplication).appContainer

val Activity.appContainer: AppContainer
    get() = (application as MagicApplication).appContainer

val Fragment.appContainer: AppContainer
    get() = requireActivity().appContainer
