package com.magic.calculator.logic.strategies

import com.magic.calculator.logic.MagicEngine
import java.util.Calendar

/**
 * 1. Digit Flip Strategy
 */
class DigitFlipStrategy : MagicTrickStrategy {
    override val id: String = "DIGIT_FLIP"
    override val displayName: String = "Digital Flip"
    override val isPremium: Boolean = false
    override val requiresSensor: Boolean = true

    override fun execute(input: String, context: MagicExecutionContext): MagicExecutionResult {
        return MagicExecutionResult(
            trickType = MagicEngine.TrickType.DIGIT_FLIP,
            displayValue = context.customFlipResult,
            trickName = displayName,
            description = "Magic has flipped the digits!"
        )
    }
}

/**
 * 2. Lucky Number Strategy
 */
class LuckyNumberStrategy : MagicTrickStrategy {
    override val id: String = "LUCKY"
    override val displayName: String = "Lucky Number"
    override val isPremium: Boolean = false
    override val requiresSensor: Boolean = false

    override fun execute(input: String, context: MagicExecutionContext): MagicExecutionResult {
        return MagicExecutionResult(
            trickType = MagicEngine.TrickType.LUCKY,
            displayValue = context.forcedLuckyNumber,
            trickName = displayName,
            description = "The universe chose ${context.forcedLuckyNumber} for you!"
        )
    }
}

/**
 * 3. Phone Number Force Strategy
 */
class PhoneNumberForceStrategy : MagicTrickStrategy {
    override val id: String = "PHONE_NUMBER_FORCE"
    override val displayName: String = "Phone Number Force"
    override val isPremium: Boolean = false
    override val requiresSensor: Boolean = false

    override fun execute(input: String, context: MagicExecutionContext): MagicExecutionResult {
        return MagicExecutionResult(
            trickType = MagicEngine.TrickType.PHONE_NUMBER_FORCE,
            displayValue = context.forcedPhoneNumber,
            trickName = displayName,
            description = "Magic has forced the phone number!"
        )
    }
}

/**
 * 4. Toxic Time Force Strategy
 */
class ToxicTimeStrategy : MagicTrickStrategy {
    override val id: String = "TOXIC_TIME"
    override val displayName: String = "Toxic Time Force"
    override val isPremium: Boolean = true
    override val requiresSensor: Boolean = false

    override fun execute(input: String, context: MagicExecutionContext): MagicExecutionResult {
        val output = if (context.toxicValue.isNotEmpty()) context.toxicValue else context.forcedLuckyNumber
        return MagicExecutionResult(
            trickType = MagicEngine.TrickType.TOXIC_TIME,
            displayValue = output,
            trickName = displayName,
            description = "The calculation matched live reality!"
        )
    }
}

/**
 * 5. Ghost Typing Strategy
 */
class GhostTypingStrategy : MagicTrickStrategy {
    override val id: String = "GHOST_TYPING"
    override val displayName: String = "Ghost Typing Reveal"
    override val isPremium: Boolean = true
    override val requiresSensor: Boolean = true

    override fun execute(input: String, context: MagicExecutionContext): MagicExecutionResult {
        return MagicExecutionResult(
            trickType = MagicEngine.TrickType.GHOST_TYPING,
            displayValue = context.ghostTypingNumber,
            trickName = displayName,
            description = "The invisible spirits typed the prediction!",
            isDeferredAnimation = true
        )
    }
}

/**
 * 6. Ghost Recovery Strategy
 */
class GhostRecoveryStrategy : MagicTrickStrategy {
    override val id: String = "REAPPEARING_RESULT"
    override val displayName: String = "Ghost Recovery"
    override val isPremium: Boolean = true
    override val requiresSensor: Boolean = true

    override fun execute(input: String, context: MagicExecutionContext): MagicExecutionResult {
        return MagicExecutionResult(
            trickType = MagicEngine.TrickType.REAPPEARING_RESULT,
            displayValue = input,
            trickName = displayName,
            description = "Vanished numbers have resurrected!"
        )
    }
}

/**
 * 7. Step-by-Step Reveal Strategy
 */
class StepRevealStrategy : MagicTrickStrategy {
    override val id: String = "STEP_REVEAL"
    override val displayName: String = "Step-by-Step Reveal"
    override val isPremium: Boolean = true
    override val requiresSensor: Boolean = true

    override fun execute(input: String, context: MagicExecutionContext): MagicExecutionResult {
        return MagicExecutionResult(
            trickType = MagicEngine.TrickType.STEP_REVEAL,
            displayValue = input,
            trickName = displayName,
            description = "Each gesture reveals another piece of the mystery."
        )
    }
}

/**
 * 8. Contact Force Strategy
 */
class ContactForceStrategy : MagicTrickStrategy {
    override val id: String = "CONTACT_FORCE"
    override val displayName: String = "Contact Force Predictor"
    override val isPremium: Boolean = true
    override val requiresSensor: Boolean = false

    override fun execute(input: String, context: MagicExecutionContext): MagicExecutionResult {
        return MagicExecutionResult(
            trickType = MagicEngine.TrickType.PHONE_NUMBER_FORCE,
            displayValue = context.forcedPhoneNumber,
            trickName = displayName,
            description = "Spectator's contact prediction locked!"
        )
    }
}

/**
 * 9. Toxic Gallery Strategy
 */
class ToxicGalleryStrategy : MagicTrickStrategy {
    override val id: String = "TOXIC_GALLERY"
    override val displayName: String = "Toxic Gallery Force"
    override val isPremium: Boolean = true
    override val requiresSensor: Boolean = false

    override fun execute(input: String, context: MagicExecutionContext): MagicExecutionResult {
        return MagicExecutionResult(
            trickType = MagicEngine.TrickType.TOXIC_TIME,
            displayValue = context.toxicValue,
            trickName = displayName,
            description = "Gallery clock hand forced perfectly!"
        )
    }
}

/**
 * 10. Inversion Strategy
 */
class InversionStrategy : MagicTrickStrategy {
    override val id: String = "INVERSION"
    override val displayName: String = "Number Inversion"
    override val isPremium: Boolean = false
    override val requiresSensor: Boolean = false

    override fun execute(input: String, context: MagicExecutionContext): MagicExecutionResult {
        return MagicExecutionResult(
            trickType = MagicEngine.TrickType.INVERSION,
            displayValue = input.replace('6', '9'),
            trickName = displayName,
            description = "6s have been flipped to 9s!"
        )
    }
}

/**
 * 11. Birthday Strategy
 */
class BirthdayStrategy : MagicTrickStrategy {
    override val id: String = "BIRTHDAY"
    override val displayName: String = "Birthday Magic"
    override val isPremium: Boolean = false
    override val requiresSensor: Boolean = false

    override fun execute(input: String, context: MagicExecutionContext): MagicExecutionResult {
        val birthYear = input.toIntOrNull() ?: 2000
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val age = currentYear - birthYear
        return MagicExecutionResult(
            trickType = MagicEngine.TrickType.BIRTHDAY,
            displayValue = age.toString(),
            trickName = displayName,
            description = "Born in $birthYear? You are $age years old!"
        )
    }
}

/**
 * 12. Mirror Reversal Strategy
 */
class MirrorStrategy : MagicTrickStrategy {
    override val id: String = "MIRROR"
    override val displayName: String = "Mirror Reversal"
    override val isPremium: Boolean = false
    override val requiresSensor: Boolean = false

    override fun execute(input: String, context: MagicExecutionContext): MagicExecutionResult {
        return MagicExecutionResult(
            trickType = MagicEngine.TrickType.MIRROR,
            displayValue = input.reversed(),
            trickName = displayName,
            description = "Your number has been perfectly mirrored!"
        )
    }
}
