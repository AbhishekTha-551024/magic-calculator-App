package com.magic.calculator.logic.strategies

import com.magic.calculator.logic.MagicEngine

/**
 * Central registry managing all extensible magic trick strategies.
 * Adding or modifying tricks in future updates requires registering them here.
 */
class MagicRegistry {

    private val strategies = mutableMapOf<String, MagicTrickStrategy>()

    init {
        registerDefaultStrategies()
    }

    private fun registerDefaultStrategies() {
        register(DigitFlipStrategy())
        register(LuckyNumberStrategy())
        register(PhoneNumberForceStrategy())
        register(ToxicTimeStrategy())
        register(GhostTypingStrategy())
        register(GhostRecoveryStrategy())
        register(StepRevealStrategy())
        register(ContactForceStrategy())
        register(ToxicGalleryStrategy())
        register(InversionStrategy())
        register(BirthdayStrategy())
        register(MirrorStrategy())
    }

    fun register(strategy: MagicTrickStrategy) {
        strategies[strategy.id] = strategy
    }

    fun getStrategy(id: String): MagicTrickStrategy? {
        return strategies[id]
    }

    fun getAllStrategies(): List<MagicTrickStrategy> {
        return strategies.values.toList()
    }

    fun isPremiumTrick(id: String): Boolean {
        return strategies[id]?.isPremium ?: false
    }

    fun requiresSensor(id: String): Boolean {
        return strategies[id]?.requiresSensor ?: false
    }

    /**
     * Executes the strategy by key, or falls back to no-op.
     */
    fun executeTrick(id: String, input: String, context: MagicExecutionContext): MagicExecutionResult? {
        return strategies[id]?.execute(input, context)
    }

    companion object {
        @Volatile
        private var instance: MagicRegistry? = null

        fun getInstance(): MagicRegistry {
            return instance ?: synchronized(this) {
                instance ?: MagicRegistry().also { instance = it }
            }
        }
    }
}
