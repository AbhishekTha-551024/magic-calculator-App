package com.magic.calculator.logic

import com.magic.calculator.logic.strategies.MagicExecutionContext
import com.magic.calculator.logic.strategies.MagicRegistry
import java.util.Calendar

/**
 * The Brain of the Magic Calculator.
 * Identifies and performs magical transformations on numbers using [MagicRegistry] strategies.
 */
class MagicEngine(private val registry: MagicRegistry = MagicRegistry.getInstance()) {

    enum class TrickType {
        INVERSION,          // 6666 → 9999
        PALINDROME,         // 12321 stays, with mirror visual
        DIGIT_FLIP,         // custom trigger → custom result
        LUCKY,              // Any → forced lucky number
        BIRTHDAY,           // Birth year → age
        MIRROR,             // 1234 → 4321
        PHONE_NUMBER_FORCE, // 10 digits → forced phone number
        TOXIC_TIME,         // current time/date/battery
        GHOST_TYPING,       // numbers type themselves
        REAPPEARING_RESULT, // deleted numbers come back
        STEP_REVEAL,        // reveal digits one-by-one with sensors
        NONE
    }

    data class TrickResult(
        val type: TrickType,
        val displayValue: String,
        val trickName: String,
        val description: String
    )

    /**
     * Identifies the best trick for the given input.
     */
    fun identifyTrick(input: String, customTrigger: String = "66"): TrickType {
        val cleaned = input.trim()
        if (cleaned.isEmpty() || cleaned == "0") return TrickType.LUCKY

        return when {
            cleaned == customTrigger -> TrickType.DIGIT_FLIP
            cleaned.all { it == '6' } && cleaned.length >= 3 -> TrickType.INVERSION
            cleaned == "69" || cleaned == "96" -> TrickType.DIGIT_FLIP
            cleaned.length >= 4 && cleaned == cleaned.reversed() && !cleaned.all { it == cleaned[0] } -> TrickType.PALINDROME
            cleaned == "7" || cleaned == "77" || cleaned == "777" -> TrickType.LUCKY
            cleaned.length == 4 && (cleaned.startsWith("19") || cleaned.startsWith("200")) && cleaned.toIntOrNull() != null -> TrickType.BIRTHDAY
            cleaned.length == 10 && cleaned.all { it.isDigit() } -> TrickType.PHONE_NUMBER_FORCE
            cleaned.length >= 3 && cleaned.all { it.isDigit() } -> TrickType.MIRROR
            else -> TrickType.LUCKY
        }
    }

    /**
     * Performs the magic transformation and returns a full TrickResult.
     */
    fun performMagic(
        input: String, 
        type: TrickType, 
        forcedPhone: String = "8815114272", 
        forcedLucky: String = "777",
        flipResult: String = "699"
    ): TrickResult {
        val cleaned = input.trim()

        val strategyKey = when (type) {
            TrickType.DIGIT_FLIP -> "DIGIT_FLIP"
            TrickType.LUCKY -> "LUCKY"
            TrickType.PHONE_NUMBER_FORCE -> "PHONE_NUMBER_FORCE"
            TrickType.TOXIC_TIME -> "TOXIC_TIME"
            TrickType.GHOST_TYPING -> "GHOST_TYPING"
            TrickType.REAPPEARING_RESULT -> "REAPPEARING_RESULT"
            TrickType.STEP_REVEAL -> "STEP_REVEAL"
            TrickType.INVERSION -> "INVERSION"
            TrickType.BIRTHDAY -> "BIRTHDAY"
            TrickType.MIRROR -> "MIRROR"
            else -> null
        }

        if (strategyKey != null) {
            try {
                val context = MagicExecutionContext(
                    forcedPhoneNumber = forcedPhone,
                    forcedLuckyNumber = forcedLucky,
                    customFlipResult = flipResult,
                    toxicValue = forcedLucky
                )
                val res = registry.executeTrick(strategyKey, cleaned, context)
                if (res != null) {
                    return TrickResult(
                        type = type,
                        displayValue = res.displayValue,
                        trickName = res.trickName,
                        description = res.description
                    )
                }
            } catch (e: Throwable) {
                CrashReportingManager.logNonFatal(
                    e,
                    "MagicEngine execution failed for trick: $strategyKey with input: $cleaned"
                )
            }
        }

        return when (type) {
            TrickType.PALINDROME -> TrickResult(
                type = type,
                displayValue = cleaned,
                trickName = "Palindrome Magic",
                description = "This number reads the same forwards & backwards!"
            )
            else -> TrickResult(
                type = type,
                displayValue = cleaned,
                trickName = "No Trick",
                description = ""
            )
        }
    }
}