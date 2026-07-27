package com.magic.calculator.logic

import java.util.Calendar

/**
 * The Brain  the Magic Calculator.
 * Identifies and performs magical transformations numbers.
 */
class MagicEngine {

    enum class TrickType {
        INVERSION,    // 6666 → 9999
        PALINDROME,   // 12321 stays, with mirror visual
        DIGIT_FLIP,   // custom trigger → custom result
        LUCKY,        // Any → forced lucky number
        BIRTHDAY,     // Birth year → age
        MIRROR,       // 1234 → 4321
        PHONE_NUMBER_FORCE, // 10 digits → forced phone number
        TOXIC_TIME,   // current time/date/battery
        GHOST_TYPING, // numbers type themselves
        REAPPEARING_RESULT, // deleted numbers come back
        STEP_REVEAL,  // reveal digits one-by-one with sensors
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
        return when (type) {
            TrickType.INVERSION -> TrickResult(
                type = type,
                displayValue = cleaned.replace('6', '9'),
                trickName = "Number Inversion",
                description = "6s have been flipped to 9s!"
            )
            TrickType.DIGIT_FLIP -> TrickResult(
                type = type,
                displayValue = flipResult,
                trickName = "Digit Flip",
                description = "Magic has flipped the digits!"
            )
            TrickType.PALINDROME -> TrickResult(
                type = type,
                displayValue = cleaned,
                trickName = "Palindrome Magic",
                description = "This number reads the same forwards & backwards!"
            )
            TrickType.LUCKY -> TrickResult(
                type = type,
                displayValue = forcedLucky,
                trickName = "Lucky Number",
                description = "The universe chose $forcedLucky for you!"
            )
            TrickType.BIRTHDAY -> {
                val birthYear = cleaned.toIntOrNull() ?: 2000
                val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                val age = currentYear - birthYear
                TrickResult(
                    type = type,
                    displayValue = age.toString(),
                    trickName = "Birthday Magic",
                    description = "Born in $birthYear? You are $age years old!"
                )
            }
            TrickType.MIRROR -> TrickResult(
                type = type,
                displayValue = cleaned.reversed(),
                trickName = "Mirror Reversal",
                description = "Your number has been perfectly mirrored!"
            )
            TrickType.PHONE_NUMBER_FORCE -> TrickResult(
                type = type,
                displayValue = forcedPhone,
                trickName = "Phone Magic",
                description = "Magic has forced your number!"
            )
            TrickType.TOXIC_TIME -> TrickResult(
                type = type,
                displayValue = forcedLucky, // reuse forcedLucky parameter as the pre-calculated toxic value
                trickName = "Toxic Force",
                description = "The numbers aligned with reality!"
            )
            TrickType.GHOST_TYPING -> TrickResult(
                type = type,
                displayValue = cleaned, // ghost typing doesn't change the value instantly
                trickName = "Ghost Typing",
                description = "The spirits are typing..."
            )
            TrickType.REAPPEARING_RESULT -> TrickResult(
                type = type,
                displayValue = cleaned, // handled in ViewModel
                trickName = "Ghost Recovery",
                description = "The deleted numbers have returned!"
            )
            TrickType.STEP_REVEAL -> TrickResult(
                type = type,
                displayValue = cleaned, // handled in ViewModel step-by-step
                trickName = "Step-by-Step Reveal",
                description = "Each touch unveils a mystery..."
            )
            TrickType.NONE -> TrickResult(
                type = type,
                displayValue = cleaned,
                trickName = "No Trick",
                description = ""
            )
        }
    }
}