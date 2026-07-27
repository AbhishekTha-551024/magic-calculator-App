package com.magic.calculator.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.magic.calculator.logic.MagicEngine
import com.magic.calculator.logic.SettingsManager

/**
 * State manager for the Magic Calculator.
 */
class MagicViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = MagicEngine()
    private val settingsManager = SettingsManager(application)

    private val _displayText = MutableLiveData("0")
    val displayText: LiveData<String> = _displayText

    private val _formulaText = MutableLiveData("")
    val formulaText: LiveData<String> = _formulaText

    data class GhostDigit(val digit: String, val x: Float, val y: Float, val isLast: Boolean = false)
    private val _newGhostDigit = MutableLiveData<GhostDigit?>()
    val newGhostDigit: LiveData<GhostDigit?> = _newGhostDigit

    private val _slidingDigit = MutableLiveData<String?>()
    val slidingDigit: LiveData<String?> = _slidingDigit

    private val _shakeProgress = MutableLiveData(0)

    private val _magicTriggered = MutableLiveData<MagicEngine.TrickType>()
    val magicTriggered: LiveData<MagicEngine.TrickType> = _magicTriggered
    
    var isWaitingForShake = false

    private val _secretUnlocked = MutableLiveData<Boolean>(false)
    val secretUnlocked: LiveData<Boolean> = _secretUnlocked

    private var currentInput = ""
    private var firstOperand = 0.0
    private var currentOperator = ""
    private var isNewInput = true
    private var hasOperatorPending = false
    private var secretCodeSequence = ""
    private var ghostTypingRunnable: Runnable? = null
    private val ghostTypingHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var lastFullResult = ""
    private var revealDigitIndex = 0
    var isStepRevealActive = false // To distinguish typing from magic

    fun onNumberClick(number: String) {
        if (isStepRevealActive) return

        if (number == "7" || number == "4" || number == "1") {
            secretCodeSequence += number
        } else {
            secretCodeSequence = ""
        }

        if (number == "." && currentInput.contains(".")) return
        if (number == "0" && currentInput == "0") return

        if (isNewInput) {
            currentInput = if (number == ".") "0." else number
            isNewInput = false
        } else {
            if (currentInput == "0" && number != ".") {
                currentInput = number
            } else {
                currentInput += number
            }
        }
        _displayText.value = currentInput
    }

    fun onOperatorClick(op: String) {
        if (isStepRevealActive) return

        if (currentInput.isNotEmpty() && hasOperatorPending && !isNewInput) {
            evaluateExpression()
        }

        if (currentInput.isNotEmpty()) {
            firstOperand = currentInput.toDoubleOrNull() ?: 0.0
        }
        currentOperator = op
        hasOperatorPending = true
        _formulaText.value = "${formatNumber(firstOperand)} $op"
        isNewInput = true
    }

    fun checkSecretUnlock(): Boolean {
        if (secretCodeSequence == "741") {
            _secretUnlocked.value = true
            secretCodeSequence = ""
            return true
        }
        return false
    }

    fun onEqualClick() {
        if (isStepRevealActive) return
        if (checkSecretUnlock()) return
        secretCodeSequence = ""

        if (!hasOperatorPending || currentOperator.isEmpty()) return
        if (currentInput.isEmpty() || isNewInput) return

        val secondNum = currentInput.toDoubleOrNull() ?: 0.0
        _formulaText.value = "${formatNumber(firstOperand)} $currentOperator ${formatNumber(secondNum)} ="

        val result = when (currentOperator) {
            "+" -> firstOperand + secondNum
            "-" -> firstOperand - secondNum
            "*", "×" -> firstOperand * secondNum
            "/", "÷" -> {
                if (secondNum == 0.0) {
                    _displayText.value = "Error"
                    resetState()
                    return
                }
                firstOperand / secondNum
            }
            else -> secondNum
        }

        applyResult(result)
    }

    private fun resetState() {
        currentInput = ""
        currentOperator = ""
        hasOperatorPending = false
        isNewInput = true
    }

    private fun applyResult(result: Double) {
        val resultStr = formatNumber(result)
        lastFullResult = resultStr
        
        // Force Magic Trick if active
        val activeMagic = settingsManager.activeMagic
        if (activeMagic == "PHONE_NUMBER_FORCE" && resultStr.length == 10 && resultStr.all { it.isDigit() }) {
            currentInput = settingsManager.forcedPhoneNumber
            _displayText.value = currentInput
            _formulaText.value = "" 
            _magicTriggered.value = MagicEngine.TrickType.PHONE_NUMBER_FORCE
            isNewInput = true
            hasOperatorPending = false
            return
        }

        if (activeMagic == "TOXIC_TIME") {
            // Toxic value is passed from MainActivity via forceDisplay or calculated here if we add helpers
            // For now, let's assume we handle the trigger in MainActivity and it calls onHardwareTrigger with pre-calculated value
        }

        _displayText.value = resultStr
        currentInput = resultStr
        currentOperator = ""
        hasOperatorPending = false
        isNewInput = true
    }

    fun onClearClick() {
        stopGhostTyping()
        
        if (isStepRevealActive) {
            // If already in reveal mode, hitting AC cancels/resets it
            isStepRevealActive = false
            lastFullResult = ""
            revealDigitIndex = 0
        } else {
            val displayValue = _displayText.value ?: ""
            // If we are in Step Reveal mode, hitting AC saves the current display as the target and prepares the reveal
            if (settingsManager.activeMagic == "STEP_REVEAL" && displayValue != "0" && displayValue.isNotEmpty() && displayValue != "Error") {
                lastFullResult = displayValue
                isStepRevealActive = true
                revealDigitIndex = 0
            }
        }

        currentInput = ""
        firstOperand = 0.0
        currentOperator = ""
        hasOperatorPending = false
        isNewInput = true
        revealDigitIndex = 0

        _displayText.value = "0"
        _formulaText.value = ""
        _shakeProgress.value = 0
    }

    fun wipeMagicMemory() {
        onClearClick()
        lastFullResult = ""
        isStepRevealActive = false
    }

    fun onPlusMinusClick() {
        if (currentInput.isEmpty() || currentInput == "0" || currentInput == "Error") return
        currentInput = if (currentInput.startsWith("-")) {
            currentInput.substring(1)
        } else {
            "-$currentInput"
        }
        _displayText.value = currentInput
    }

    fun onPercentClick() {
        if (currentInput.isEmpty() || currentInput == "Error") return
        val value = currentInput.toDoubleOrNull() ?: return
        val result = value / 100.0
        applyResult(result)
    }

    fun onBackspaceClick() {
        if (currentInput.isNotEmpty() && currentInput != "0") {
            currentInput = currentInput.dropLast(1)
            if (currentInput.isEmpty() || currentInput == "-") {
                currentInput = "0"
                isNewInput = true
            } else {
                isNewInput = false
            }
            _displayText.value = currentInput
        }
    }

    // New: Force display from activity (used for Lucky Magic)
    fun forceDisplay(text: String) {
        currentInput = text
        _displayText.value = text
        isNewInput = true
    }

    // New: Handle Hardware Trigger from Activity
    fun onHardwareTrigger(forcedValue: String? = null) {
        val currentDisplay = _displayText.value ?: return
        
        val activeMagic = settingsManager.activeMagic
        if (activeMagic == "REAPPEARING_RESULT") {
            triggerReappear()
            return
        }
        
        if (activeMagic == "STEP_REVEAL") {
            // Sequential reveal is handled via specific calls
            return
        }

        if (currentDisplay == "0" && forcedValue == null) return // Allow 0 for toxic/ghost if forced

        if (activeMagic == "NONE") return

        // Determine Trick Type
        val trick = when (activeMagic) {
            "DIGIT_FLIP" -> MagicEngine.TrickType.DIGIT_FLIP
            "LUCKY" -> MagicEngine.TrickType.LUCKY
            "PHONE_NUMBER_FORCE" -> MagicEngine.TrickType.PHONE_NUMBER_FORCE
            "TOXIC_TIME" -> MagicEngine.TrickType.TOXIC_TIME
            "GHOST_TYPING" -> MagicEngine.TrickType.GHOST_TYPING
            else -> engine.identifyTrick(currentDisplay, settingsManager.customFlipTrigger)
        }

        val result = engine.performMagic(
            input = currentDisplay, 
            type = trick,
            forcedPhone = settingsManager.forcedPhoneNumber,
            forcedLucky = forcedValue ?: settingsManager.forcedLuckyNumber, // Use forcedValue for Toxic
            flipResult = settingsManager.customFlipResult
        )
        
        if (trick == MagicEngine.TrickType.GHOST_TYPING) {
            startGhostTyping(settingsManager.ghostTypingNumber)
        } else {
            _displayText.value = result.displayValue
            currentInput = result.displayValue
            _magicTriggered.value = trick
            isNewInput = true
        }
    }

    private fun startGhostTyping(target: String) {
        stopGhostTyping()
        _displayText.value = ""
        currentInput = ""
        
        var index = 0
        val typist = object : Runnable {
            override fun run() {
                if (index < target.length) {
                    currentInput += target[index]
                    _displayText.value = currentInput
                    index++
                    ghostTypingHandler.postDelayed(this, 150)
                } else {
                    _magicTriggered.value = MagicEngine.TrickType.GHOST_TYPING
                    isNewInput = true
                    ghostTypingRunnable = null
                }
            }
        }
        ghostTypingRunnable = typist
        ghostTypingHandler.postDelayed(typist, 500)
    }

    fun stopGhostTyping() {
        ghostTypingRunnable?.let { ghostTypingHandler.removeCallbacks(it) }
        ghostTypingRunnable = null
    }

    private fun triggerReappear() {
        val current = _displayText.value ?: ""
        // Magic restriction: Only works if exactly one digit is on screen
        if (lastFullResult.isNotEmpty() && current.length == 1 && current != lastFullResult) {
            currentInput = lastFullResult
            _displayText.value = currentInput
            _magicTriggered.value = MagicEngine.TrickType.REAPPEARING_RESULT
            isNewInput = true
        }
    }

    fun startStepReveal() {
        val current = _displayText.value ?: ""
        if (current.isNotEmpty() && current != "0" && current != "Error") {
            // ONLY ACTIVATE FOR 5 DIGIT NUMBERS
            if (current.length != 5) return

            lastFullResult = current
            _displayText.value = "0"
            currentInput = ""
            revealDigitIndex = 0
            isNewInput = true
            isStepRevealActive = true // ARMED
            _newGhostDigit.value = null
        }
    }

    /** Called when user presses = with a number in STEP_REVEAL mode.
     *  Saves the number as the forced reveal number and starts the sequence. */
    fun saveAndStartReveal(number: String) {
        // ONLY ACTIVATE FOR 5 DIGIT NUMBERS
        if (number.length != 5) return

        lastFullResult = number
        _displayText.value = "0"
        currentInput = ""
        revealDigitIndex = 0
        isNewInput = true
        isStepRevealActive = true // ARMED
        _newGhostDigit.value = null
    }

    fun revealNextDigit() {
        // Fallback for general triggers
        revealStepWithTrigger("TOUCH")
    }

    fun peekNextDigit(): String? {
        if (lastFullResult.isEmpty() || revealDigitIndex >= lastFullResult.length) return null
        return lastFullResult[revealDigitIndex].toString()
    }

    private var lastRevealTime = 0L
    private val REVEAL_COOLDOWN = 2000L // 2.0 seconds between steps

    fun getRevealDigitIndex(): Int = revealDigitIndex

    fun revealStepWithTrigger(triggerType: String, x: Float = 0f, y: Float = 0f, isTop: Boolean = false) {
        if (!isStepRevealActive) return
        if (lastFullResult.isEmpty() || revealDigitIndex >= lastFullResult.length) return

        // Anti-flicker/Double-trigger protection
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastRevealTime < REVEAL_COOLDOWN) return

        // 1st digit: TOUCH
        // 2nd digit: PROXIMITY
        // 3rd digit: FACE_DOWN
        // 4th digit: LIGHT
        // 5th digit: TOUCH
        val isLastDigit = (revealDigitIndex == lastFullResult.length - 1) && (lastFullResult.length > 1)
        
        val sequenceList = settingsManager.stepRevealSequence.split(",").map { it.trim().uppercase() }
        val requiredTrigger = if (isLastDigit) "TOUCH" else {
            if (revealDigitIndex < sequenceList.size) sequenceList[revealDigitIndex] else "TOUCH"
        }

        if (triggerType == requiredTrigger || requiredTrigger == "ANY") {
            lastRevealTime = currentTime
            
            val digit = lastFullResult[revealDigitIndex].toString()

            if (isLastDigit) {
                // The last digit is placed at the tapped spot first
                if (isTop) {
                    _newGhostDigit.value = GhostDigit(digit, x, y, isLast = true)
                    revealDigitIndex++
                    isNewInput = true
                    _magicTriggered.value = MagicEngine.TrickType.STEP_REVEAL
                }
                return 
            }

            // Intermediate digits: ignore top touches, always go to main display
            if (isTop) return

            currentInput += digit
            _displayText.value = currentInput
            
            revealDigitIndex++
            isNewInput = true
            _magicTriggered.value = MagicEngine.TrickType.STEP_REVEAL
        }
    }

    fun finalizeSlidingReveal() {
        if (lastFullResult.isEmpty()) return
        val lastDigit = lastFullResult.last().toString()
        // Prepend the last digit to the front
        currentInput = lastDigit + currentInput
        _displayText.value = currentInput
        _slidingDigit.value = null
        isWaitingForShake = true
    }

    fun jumpLastDigitToEnd() {
        if (currentInput.length < 2) return
        val firstChar = currentInput[0]
        currentInput = currentInput.substring(1) + firstChar
        _displayText.value = currentInput
        isWaitingForShake = false
        _magicTriggered.value = MagicEngine.TrickType.STEP_REVEAL
    }

    /** Called by Activity when animation finishes (NOT USED FOR NOW AS PER USER REQUEST) */
    fun finalizeStepReveal() {
        // We now update instantly in revealStepWithTrigger
    }
    
    private val _lastTapCoords = MutableLiveData<Pair<Float, Float>>()
    val lastTapCoords: LiveData<Pair<Float, Float>> = _lastTapCoords

    fun onShake(count: Int) {
        _shakeProgress.value = count
        if (count >= 3) {
            onHardwareTrigger()
            _shakeProgress.value = 0
        }
    }

    fun onMagicConsumed() {
        _magicTriggered.value = MagicEngine.TrickType.NONE
    }

    fun onSecretConsumed() {
        _secretUnlocked.value = false
    }

    private fun evaluateExpression() {
        val secondNum = currentInput.toDoubleOrNull() ?: 0.0
        val result = when (currentOperator) {
            "+" -> firstOperand + secondNum
            "-" -> firstOperand - secondNum
            "*", "×" -> firstOperand * secondNum
            "/", "÷" -> {
                if (secondNum == 0.0) 0.0 else firstOperand / secondNum
            }
            else -> secondNum
        }
        val resultStr = formatNumber(result)
        _displayText.value = resultStr
        currentInput = resultStr
        firstOperand = result
    }

    private fun formatNumber(value: Double): String {
        return if (value % 1 == 0.0 && !value.isInfinite() && !value.isNaN() && value <= Long.MAX_VALUE && value >= Long.MIN_VALUE) {
            value.toLong().toString()
        } else {
            value.toString()
        }
    }

    /**
     * P2P Mirror Typing: updates the calculator display from a remote partner's input.
     * Uses a flag to prevent the display observer in MainActivity from echoing back to Firestore.
     */
    var isReceivingRemote = false

    fun setRemoteDisplay(display: String, formula: String) {
        isReceivingRemote = true
        currentInput = display
        _displayText.value = display
        _formulaText.value = formula
        isNewInput = true
        isReceivingRemote = false
    }
}
