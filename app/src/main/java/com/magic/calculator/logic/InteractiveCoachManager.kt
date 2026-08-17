package com.magic.calculator.logic

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

/**
 * InteractiveCoachManager
 *
 * Coordinates live interactive walkthrough steps for the Onboarding Basic Tour:
 * 1. Master Backdoor Entry (7 -> 4 -> 1 -> =)
 * 2. Secret Switchboard Configuration (Digital Flip ON, Proximity Trigger ON, Save parameters)
 * 3. Live Performance on Calculator (66 -> = -> Wave -> 99)
 */
object InteractiveCoachManager {

    enum class TrainingStep(val stepNumber: Int, val totalSteps: Int, val title: String, val instruction: String) {
        OFF(0, 9, "", ""),

        // ── Full Basic Tour & Digital Flip Track ─────────────────────
        STEP_1_TAP_7(1, 9, "Master Backdoor Entry", "Tap number 7"),
        STEP_2_TAP_4(2, 9, "Master Backdoor Entry", "Tap number 4"),
        STEP_3_TAP_1(3, 9, "Master Backdoor Entry", "Tap number 1"),
        STEP_4_TAP_EQUAL(4, 9, "Master Backdoor Entry", "Tap '=' to unlock Backstage!"),
        STEP_5_CONFIG_FLIP(5, 9, "Configure Magic Switch", "Turn ON 'Digital Flip' switch"),
        STEP_6_CONFIG_PROXIMITY(6, 9, "Select Sensor Trigger", "Turn ON 'Wave (Proximity)' sensor trigger"),
        STEP_6B_OPEN_EDIT_TAB(7, 9, "Edit Parameters", "Tap 'Edit' icon at top to view Flip parameters"),
        STEP_6C_SAVE_FLIP_PARAMS(8, 9, "Save Parameters", "Tap 'Save Flip' to confirm trigger 66 & result 99"),
        STEP_7_TAP_BACK(9, 9, "Start Performance", "Tap 'Back' at bottom to perform on Calculator!"),
        STEP_8_PERFORM_66(1, 3, "Digital Flip Performance", "Type '66' on the keypad"),
        STEP_9_PERFORM_EQUAL(2, 3, "Digital Flip Performance", "Tap '=' to compute normally"),
        STEP_10_PERFORM_WAVE(3, 3, "Digital Flip Performance", "Wave your hand over top of phone to flip 66 into 99!"),

        TRAINING_COMPLETED(9, 9, "Training Complete! 🎉", "You've mastered the Magic Calculator!")
    }

    private val _currentStep = MutableLiveData<TrainingStep>(TrainingStep.OFF)
    val currentStep: LiveData<TrainingStep> get() = _currentStep

    val isTrainingActive: Boolean
        get() = _currentStep.value != null && _currentStep.value != TrainingStep.OFF && _currentStep.value != TrainingStep.TRAINING_COMPLETED

    /** True when a new user skipped the tour — used to show the 741= hint banner. */
    var wasSkippedByNewUser: Boolean = false
        private set

    fun startTraining() {
        if (_currentStep.value == TrainingStep.OFF || _currentStep.value == null) {
            _currentStep.value = TrainingStep.STEP_1_TAP_7
        }
    }

    fun restartTraining() {
        _currentStep.value = TrainingStep.STEP_1_TAP_7
    }

    fun startDigitalFlipPractice() {
        _currentStep.value = TrainingStep.STEP_8_PERFORM_66
    }

    fun skipTraining(context: Context, newUserSkipped: Boolean = false) {
        _currentStep.value = TrainingStep.OFF
        if (newUserSkipped) wasSkippedByNewUser = true
        OnboardingManager.markFreeOnboardingCompleted(context)
    }

    fun clearSkippedFlag() {
        wasSkippedByNewUser = false
    }

    fun setStep(step: TrainingStep) {
        _currentStep.value = step
    }

    fun notifyDigitPressed(digit: String) {
        when (_currentStep.value) {
            TrainingStep.STEP_1_TAP_7 -> if (digit == "7") _currentStep.value = TrainingStep.STEP_2_TAP_4
            TrainingStep.STEP_2_TAP_4 -> if (digit == "4") _currentStep.value = TrainingStep.STEP_3_TAP_1
            TrainingStep.STEP_3_TAP_1 -> if (digit == "1") _currentStep.value = TrainingStep.STEP_4_TAP_EQUAL
            else -> {}
        }
    }

    fun notifyEqualPressed(onUnlockTriggered: () -> Unit = {}) {
        when (_currentStep.value) {
            TrainingStep.STEP_4_TAP_EQUAL -> {
                _currentStep.value = TrainingStep.STEP_5_CONFIG_FLIP
                onUnlockTriggered()
            }
            TrainingStep.STEP_9_PERFORM_EQUAL -> {
                _currentStep.value = TrainingStep.STEP_10_PERFORM_WAVE
            }
            else -> {}
        }
    }

    fun notifyFlipSwitched(enabled: Boolean) {
        if (_currentStep.value == TrainingStep.STEP_5_CONFIG_FLIP && enabled) {
            _currentStep.value = TrainingStep.STEP_6_CONFIG_PROXIMITY
        }
    }

    fun notifyProximitySwitched(enabled: Boolean) {
        if (_currentStep.value == TrainingStep.STEP_6_CONFIG_PROXIMITY && enabled) {
            _currentStep.value = TrainingStep.STEP_6B_OPEN_EDIT_TAB
        }
    }

    fun notifyEditTabOpened() {
        if (_currentStep.value == TrainingStep.STEP_6B_OPEN_EDIT_TAB) {
            _currentStep.value = TrainingStep.STEP_6C_SAVE_FLIP_PARAMS
        }
    }

    fun notifyFlipSaved() {
        if (_currentStep.value == TrainingStep.STEP_6C_SAVE_FLIP_PARAMS) {
            _currentStep.value = TrainingStep.STEP_7_TAP_BACK
        }
    }

    fun notifyBackToCalc() {
        if (_currentStep.value == TrainingStep.STEP_7_TAP_BACK) {
            _currentStep.value = TrainingStep.STEP_8_PERFORM_66
        }
    }

    fun notifyMagicFlipped(context: Context) {
        if (_currentStep.value == TrainingStep.STEP_10_PERFORM_WAVE) {
            _currentStep.value = TrainingStep.TRAINING_COMPLETED
            OnboardingManager.markFreeOnboardingCompleted(context)
        }
    }
}
