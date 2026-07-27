package com.magic.calculator

import android.os.Bundle
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.magic.calculator.databinding.ActivitySettingsBinding
import com.magic.calculator.logic.SettingsManager

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var settingsManager: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settingsManager = SettingsManager(this)

        setupUI()
        setupListeners()
    }

    private fun setupUI() {
        // Sensitivity (Range: 5 to 30, Progress: 0 to 25)
        val sensitivity = settingsManager.shakeSensitivity
        binding.seekBarSensitivity.progress = (sensitivity - 5f).toInt().coerceIn(0, 25)
        binding.textSensitivityValue.text = "${sensitivity.toInt()} m/s²"

        // Volume
        binding.seekBarSoundVolume.progress = settingsManager.soundVolume
        
        // Haptic
        binding.seekBarHapticIntensity.progress = settingsManager.hapticIntensity
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.seekBarSensitivity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress + 5f
                binding.textSensitivityValue.text = "${value.toInt()} m/s²"
                if (fromUser) settingsManager.shakeSensitivity = value
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.seekBarSoundVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) settingsManager.soundVolume = progress
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.seekBarHapticIntensity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) settingsManager.hapticIntensity = progress
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }
}