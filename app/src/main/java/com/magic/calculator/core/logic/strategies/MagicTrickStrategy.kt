package com.magic.calculator.core.logic.strategies

import com.magic.calculator.core.logic.MagicEngine

/**
 * Execution context carrying parameters and state for trick evaluation.
 */
data class MagicExecutionContext(
    val forcedPhoneNumber: String = "8815114272",
    val forcedLuckyNumber: String = "777",
    val customFlipTrigger: String = "66",
    val customFlipResult: String = "699",
    val toxicValue: String = "",
    val ghostTypingNumber: String = "1234",
    val ghostTypingDelay: Int = 3
)

/**
 * Result returned by a magic trick strategy execution.
 */
data class MagicExecutionResult(
    val trickType: MagicEngine.TrickType,
    val displayValue: String,
    val trickName: String,
    val description: String,
    val isDeferredAnimation: Boolean = false
)

/**
 * Strategy interface for extensible magic tricks.
 * Adding a new trick in the future only requires implementing this interface
 * and registering it in [MagicRegistry].
 */
interface MagicTrickStrategy {
    val id: String
    val displayName: String
    val isPremium: Boolean
    val requiresSensor: Boolean

    fun execute(input: String, context: MagicExecutionContext): MagicExecutionResult
}
