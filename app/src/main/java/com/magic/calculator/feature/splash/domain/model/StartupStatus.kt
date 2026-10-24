package com.magic.calculator.feature.splash.domain.model

sealed class StartupStatus {
    object Initializing : StartupStatus()
    data class CheckingUpdates(val message: String = "Checking for updates...") : StartupStatus()
    data class SyncingProfile(val message: String = "Syncing profile...") : StartupStatus()
    data class Ready(val destination: SplashDestination, val message: String = "Ready...") : StartupStatus()
}
