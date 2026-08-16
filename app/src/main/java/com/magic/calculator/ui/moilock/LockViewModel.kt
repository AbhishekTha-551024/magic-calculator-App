package com.magic.calculator.ui.moilock

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.magic.calculator.logic.PinManager

class LockViewModel(application: Application) : AndroidViewModel(application) {
    private val pinManager = PinManager(application)
    
    private val _currentPin = MutableLiveData<String>("")
    val currentPin: LiveData<String> = _currentPin

    private val _unlockStatus = MutableLiveData<UnlockStatus>()
    val unlockStatus: LiveData<UnlockStatus> = _unlockStatus

    val fakeDate: String get() = pinManager.fakeDate

    enum class UnlockStatus { IDLE, SUCCESS, FAILED }

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
        if (pinManager.verifyMasterPin(pin)) {
            // Master PIN unlocks AND resets the trick so it can be performed again immediately
            pinManager.resetLock()
            _unlockStatus.value = UnlockStatus.SUCCESS
            return
        }

        if (!pinManager.isFirstUnlockDone) {
            // First time: Magic rule -> Last digit must match secret digit
            val secret = pinManager.secretDigit
            if (pin.endsWith(secret)) {
                // Magic successful! Save as permanent password
                pinManager.permanentPin = pin
                pinManager.isFirstUnlockDone = true
                _unlockStatus.value = UnlockStatus.SUCCESS
            } else {
                _unlockStatus.value = UnlockStatus.FAILED
            }
        } else {
            // Second time onwards: Must exactly match the saved permanent PIN
            if (pinManager.verifyPermanentPin(pin)) {
                _unlockStatus.value = UnlockStatus.SUCCESS
            } else {
                _unlockStatus.value = UnlockStatus.FAILED
            }
        }
    }

    fun resetStatus() {
        _unlockStatus.value = UnlockStatus.IDLE
        _currentPin.value = ""
    }

    fun resetTrickState() {
        pinManager.resetLock()
    }
}
