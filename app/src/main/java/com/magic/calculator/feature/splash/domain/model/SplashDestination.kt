package com.magic.calculator.feature.splash.domain.model

sealed class SplashDestination {
    object Main : SplashDestination()
    object Login : SplashDestination()
}
