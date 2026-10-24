package com.magic.calculator.feature.secret.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.magic.calculator.di.appContainer
import com.magic.calculator.feature.secret.domain.model.SwitchboardConfig
import com.magic.calculator.feature.secret.domain.usecase.ActivateMagicUseCase
import com.magic.calculator.feature.secret.domain.usecase.GetSecretConfigUseCase
import com.magic.calculator.feature.secret.domain.usecase.MagicNeedsSensorUseCase
import com.magic.calculator.feature.secret.domain.usecase.SaveSecretCalibrationUseCase
import com.magic.calculator.feature.secret.domain.usecase.SetActiveSensorTriggerUseCase

class SecretViewModel(
    application: Application,
    private val getSecretConfigUseCase: GetSecretConfigUseCase,
    private val activateMagicUseCase: ActivateMagicUseCase,
    private val setActiveSensorTriggerUseCase: SetActiveSensorTriggerUseCase,
    private val saveSecretCalibrationUseCase: SaveSecretCalibrationUseCase,
    private val magicNeedsSensorUseCase: MagicNeedsSensorUseCase
) : AndroidViewModel(application) {

    // Default constructor for standard ViewModelProvider
    constructor(application: Application) : this(
        application,
        GetSecretConfigUseCase(application.appContainer.secretRepository),
        ActivateMagicUseCase(application.appContainer.secretRepository),
        SetActiveSensorTriggerUseCase(application.appContainer.secretRepository),
        SaveSecretCalibrationUseCase(application.appContainer.secretRepository),
        MagicNeedsSensorUseCase(application.appContainer.secretRepository)
    )

    private val _config = MutableLiveData<SwitchboardConfig>()
    val config: LiveData<SwitchboardConfig> = _config

    init {
        refreshConfig()
    }

    fun refreshConfig() {
        _config.value = getSecretConfigUseCase()
    }

    fun activateMagic(key: String) {
        activateMagicUseCase(key)
        refreshConfig()
    }

    fun setSensorTrigger(key: String) {
        setActiveSensorTriggerUseCase(key)
        refreshConfig()
    }

    fun saveCalibration(
        sensitivity: Float,
        volume: Int,
        haptic: Int,
        forcedPhone: String,
        flipTrigger: String,
        flipResult: String
    ) {
        saveSecretCalibrationUseCase(
            sensitivity = sensitivity,
            volume = volume,
            haptic = haptic,
            forcedPhone = forcedPhone,
            flipTrigger = flipTrigger,
            flipResult = flipResult
        )
        refreshConfig()
    }

    fun magicNeedsSensor(key: String): Boolean {
        return magicNeedsSensorUseCase(key)
    }
}
