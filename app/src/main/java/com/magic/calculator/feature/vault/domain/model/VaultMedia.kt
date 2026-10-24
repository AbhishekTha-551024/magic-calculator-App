package com.magic.calculator.feature.vault.domain.model

data class VaultMedia(
    val fileName: String,
    val isPrediction: Boolean = false,
    val predictionType: PredictionType = PredictionType.NONE
) {
    enum class PredictionType {
        NONE,
        BOARD,
        FRIENDS
    }
}
