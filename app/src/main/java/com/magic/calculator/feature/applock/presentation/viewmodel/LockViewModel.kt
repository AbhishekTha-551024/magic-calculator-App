package com.magic.calculator.feature.applock.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.magic.calculator.di.appContainer
import com.magic.calculator.feature.applock.domain.model.UnlockStatus
import com.magic.calculator.feature.applock.domain.usecase.GetLockConfigUseCase
import com.magic.calculator.feature.applock.domain.usecase.ResetLockTrickUseCase
import com.magic.calculator.feature.applock.domain.usecase.VerifyLockPinUseCase

class LockViewModel(
    application: Application,
    private val verifyLockPinUseCase: VerifyLockPinUseCase,
    private val resetLockTrickUseCase: ResetLockTrickUseCase,
    private val getLockConfigUseCase: GetLockConfigUseCase
) : AndroidViewModel(application) {

    // Default constructor used by default Android ViewModelProvider.Factory
    constructor(application: Application) : this(
        application,
        VerifyLockPinUseCase(application.appContainer.appLockRepository),
        ResetLockTrickUseCase(application.appContainer.appLockRepository),
        GetLockConfigUseCase(application.appContainer.appLockRepository)
    )

    private val _currentPin = MutableLiveData<String>("")
    val currentPin: LiveData<String> = _currentPin

    private val _unlockStatus = MutableLiveData<UnlockStatus>()
    val unlockStatus: LiveData<UnlockStatus> = _unlockStatus

    val fakeDate: String get() = getLockConfigUseCase().fakeDate

    fun addDigit(digit: String) {
        val pin = _currentPin.value ?: ""
        if (pin.length < 5) {
            val newPin = pin + digit
            _currentPin.value = newPin
            if (newPin.length == 5) {
                checkPin(newPin)
            }
        }
    }

    fun removeDigit() {
        val pin = _currentPin.value ?: ""
        if (pin.isNotEmpty()) {
            _currentPin.value = pin.dropLast(1)
        }
    }

    private fun checkPin(pin: String) {
        _unlockStatus.value = verifyLockPinUseCase(pin)
    }

    fun resetStatus() {
        _unlockStatus.value = UnlockStatus.IDLE
        _currentPin.value = ""
    }

    fun resetTrickState() {
        resetLockTrickUseCase()
    }
}
