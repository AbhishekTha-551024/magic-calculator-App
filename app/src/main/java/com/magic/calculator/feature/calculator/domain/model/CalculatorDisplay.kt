package com.magic.calculator.feature.calculator.domain.model

/**
 * Domain entity representing the calculator's current display state.
 */
data class CalculatorDisplay(
    val displayText: String = "0",
    val formulaText: String = "",
    val slidingDigit: String? = null
)
