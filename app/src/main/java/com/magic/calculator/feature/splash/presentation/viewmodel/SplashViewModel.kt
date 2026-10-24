package com.magic.calculator.feature.splash.presentation.viewmodel

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.magic.calculator.di.appContainer
import com.magic.calculator.feature.splash.domain.model.SplashDestination
import com.magic.calculator.feature.splash.domain.model.StartupStatus
import com.magic.calculator.feature.splash.domain.usecase.CheckLoginStatusUseCase
import com.magic.calculator.core.logic.AppUpdateChecker
import com.magic.calculator.core.logic.OnboardingManager
import com.magic.calculator.core.logic.SubscriptionManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashViewModel(application: Application) : AndroidViewModel(application) {

    private val checkLoginStatusUseCase = CheckLoginStatusUseCase(application.appContainer.splashRepository)

    private val _startupStatus = MutableLiveData<StartupStatus>(StartupStatus.Initializing)
    val startupStatus: LiveData<StartupStatus> = _startupStatus

    fun performStartup(activity: Activity) {
        viewModelScope.launch {
            val minDisplayTime = 1600L
            val startTime = System.currentTimeMillis()

            val isLoggedIn = checkLoginStatusUseCase()

            if (isLoggedIn) {
                _startupStatus.value = StartupStatus.CheckingUpdates("Checking for updates...")
                AppUpdateChecker.checkForUpdate(activity) {
                    _startupStatus.value = StartupStatus.SyncingProfile("Syncing profile...")
                    SubscriptionManager.ensureUserDocumentCreated()
                    OnboardingManager.syncFromFirestore(activity)
                    SubscriptionManager.syncAndRestorePremiumStatus(activity) { _, _ ->
                        viewModelScope.launch {
                            val elapsed = System.currentTimeMillis() - startTime
                            if (elapsed < minDisplayTime) {
                                delay(minDisplayTime - elapsed)
                            }
                            _startupStatus.value = StartupStatus.Ready(SplashDestination.Main, "Ready...")
                        }
                    }
                }
            } else {
                _startupStatus.value = StartupStatus.Ready(SplashDestination.Login, "Ready...")
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed < minDisplayTime) {
                    delay(minDisplayTime - elapsed)
                }
                _startupStatus.value = StartupStatus.Ready(SplashDestination.Login, "Ready...")
            }
        }
    }
}
