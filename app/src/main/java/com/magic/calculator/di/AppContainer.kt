package com.magic.calculator.di

import android.app.Activity
import android.content.Context
import androidx.fragment.app.Fragment
import com.magic.calculator.MagicApplication
import com.magic.calculator.audio.SoundManager
import com.magic.calculator.haptic.HapticManager
import com.magic.calculator.logic.AuthManager
import com.magic.calculator.logic.MagicEngine
import com.magic.calculator.logic.SettingsManager
import com.magic.calculator.logic.SubscriptionManager
import com.magic.calculator.logic.strategies.MagicRegistry
import com.magic.calculator.p2p.P2PLinkManager

/**
 * Dependency Injection Container / Service Locator.
 * Centralizes lifecycle management and singleton injection across the app.
 */
class AppContainer(private val context: Context) {

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

    val p2pLinkManager: P2PLinkManager by lazy {
        P2PLinkManager.getInstance()
    }

    val authManager: AuthManager
        get() = AuthManager

    val subscriptionManager: SubscriptionManager
        get() = SubscriptionManager

    val crashReportingManager: com.magic.calculator.logic.CrashReportingManager by lazy {
        com.magic.calculator.logic.CrashReportingManager.getInstance()
    }
}

// ── Convenient Extensions for Android Components ───────────────────────────

val Context.appContainer: AppContainer
    get() = (applicationContext as MagicApplication).appContainer

val Activity.appContainer: AppContainer
    get() = (application as MagicApplication).appContainer

val Fragment.appContainer: AppContainer
    get() = requireActivity().appContainer
