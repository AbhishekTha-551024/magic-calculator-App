package com.magic.calculator.core.logic

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Live, process-wide view of the settings that hardware listeners must react to immediately.
 *
 * [SettingsManager] is a stateless SharedPreferences proxy: writing a value notifies nobody. A
 * trigger changed inside SecretActivity was therefore invisible to MainActivity's sensor loop until
 * the next `onResume`, and the microphone loop kept running for a setting the magician had already
 * turned off. Writers publish here; listeners collect from here.
 *
 * Deliberately holds no `Context`, so it stays unit-testable.
 */
object SettingsBus {

    private const val TRIGGER_NONE = "NONE"

    private val _activeSensorTrigger = MutableStateFlow(TRIGGER_NONE)

    /** Currently selected magic trigger ("NONE", "CLAP", "PROXIMITY", ...). */
    val activeSensorTrigger: StateFlow<String> = _activeSensorTrigger.asStateFlow()

    /** The only trigger that needs the microphone. */
    const val TRIGGER_CLAP = "CLAP"

    /** Called by the [SettingsManager] getter (seeding) and setter (change). */
    fun publishActiveSensorTrigger(trigger: String) {
        if (_activeSensorTrigger.value != trigger) _activeSensorTrigger.value = trigger
    }

    fun isClapTrigger(trigger: String = _activeSensorTrigger.value): Boolean = trigger == TRIGGER_CLAP

    @JvmStatic
    fun resetForTests() {
        _activeSensorTrigger.value = TRIGGER_NONE
    }
}
