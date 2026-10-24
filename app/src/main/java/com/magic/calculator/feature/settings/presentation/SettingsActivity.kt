package com.magic.calculator.feature.settings.presentation

import android.os.Bundle
import android.widget.SeekBar
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.magic.calculator.databinding.ActivitySettingsBinding
import com.magic.calculator.di.appContainer
import com.magic.calculator.feature.settings.presentation.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    private val viewModel: SettingsViewModel by viewModels {
        SettingsViewModel.Factory(appContainer.settingsRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        observeViewModel()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    // Sensitivity (Range: 5 to 30, Progress: 0 to 25)
                    val progress = (state.shakeSensitivity - 5f).toInt().coerceIn(0, 25)
                    if (binding.seekBarSensitivity.progress != progress) {
                        binding.seekBarSensitivity.progress = progress
                    }
                    binding.textSensitivityValue.text = "${state.shakeSensitivity.toInt()} m/sÂ²"

                    // Volume
                    if (binding.seekBarSoundVolume.progress != state.soundVolume) {
                        binding.seekBarSoundVolume.progress = state.soundVolume
                    }

                    // Haptic
                    if (binding.seekBarHapticIntensity.progress != state.hapticIntensity) {
                        binding.seekBarHapticIntensity.progress = state.hapticIntensity
                    }
                }
            }
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.seekBarSensitivity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress + 5f
                binding.textSensitivityValue.text = "${value.toInt()} m/sÂ²"
                if (fromUser) {
                    viewModel.updateShakeSensitivity(value)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.seekBarSoundVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    viewModel.updateSoundVolume(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.seekBarHapticIntensity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    viewModel.updateHapticIntensity(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }
}
