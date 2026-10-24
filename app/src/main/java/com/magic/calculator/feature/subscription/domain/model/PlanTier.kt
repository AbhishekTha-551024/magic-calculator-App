package com.magic.calculator.feature.subscription.domain.model

/**
 * Supported subscription plans.
 */
enum class PlanTier(
    val planCode: String,
    val displayName: String,
    val amountInINR: Int,
    val durationDays: Int
) {
    WEEKLY("WEEKLY", "Weekly Pass (â‚¹149)", 149, 7),
    MONTHLY("MONTHLY", "Monthly Pass (â‚¹299)", 299, 30),
    YEARLY("YEARLY", "1 Year VIP Pass (â‚¹1999)", 1999, 365),
    LIFETIME("LIFETIME", "1 Year VIP Pass (â‚¹1999)", 1999, 365);

    companion object {
        fun fromCode(code: String): PlanTier {
            return entries.firstOrNull { it.planCode.equals(code, ignoreCase = true) } ?: MONTHLY
        }
    }
}
