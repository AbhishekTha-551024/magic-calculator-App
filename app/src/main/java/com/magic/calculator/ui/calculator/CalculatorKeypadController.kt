package com.magic.calculator.ui.calculator

import com.magic.calculator.audio.SoundManager
import com.magic.calculator.databinding.ActivityMainBinding
import com.magic.calculator.haptic.HapticManager
import com.magic.calculator.ui.physics.PhysicsExplosionEngine
import com.magic.calculator.viewmodel.MagicViewModel

/**
 * Controller encapsulating calculator keypad button clicks, operator routing,
 * and audio/haptic feedback.
 */
class CalculatorKeypadController(
    private val binding: ActivityMainBinding,
    private val viewModel: MagicViewModel,
    private val soundManager: SoundManager,
    private val hapticManager: HapticManager,
    private val physicsEngine: PhysicsExplosionEngine,
    private val onEqualClicked: () -> Unit,
    private val onEqualLongClicked: () -> Boolean,
    private val onPanicReset: () -> Unit,
    private val onClearClicked: () -> Unit,
    private val onBackspaceClicked: () -> Unit
) {

    fun init() {
        setupNumberButtons()
        setupOperatorButtons()
        setupActionButtons()
    }

    private fun setupNumberButtons() {
        val numberButtons = listOf(
            binding.btn0 to "0", binding.btn1 to "1", binding.btn2 to "2",
            binding.btn3 to "3", binding.btn4 to "4", binding.btn5 to "5",
            binding.btn6 to "6", binding.btn7 to "7", binding.btn8 to "8",
            binding.btn9 to "9", binding.btnDot to "."
        )

        numberButtons.forEach { (btn, value) ->
            btn.setOnClickListener {
                if (physicsEngine.isButtonsExploded) return@setOnClickListener
                soundManager.playButtonTick()
                hapticManager.buttonTick()
                viewModel.onNumberClick(value)
            }
        }
    }

    private fun setupOperatorButtons() {
        binding.btnAdd.setOnClickListener { handleOperator("+") }
        binding.btnSub.setOnClickListener { handleOperator("-") }
        binding.btnMul.setOnClickListener { handleOperator("*") }
        binding.btnDiv.setOnClickListener { handleOperator("/") }
    }

    private fun handleOperator(op: String) {
        soundManager.playButtonTick()
        hapticManager.buttonTick()
        viewModel.onOperatorClick(op)
    }

    private fun setupActionButtons() {
        binding.btnBackspace.setOnClickListener {
            onBackspaceClicked()
        }

        binding.btnBackspace.setOnLongClickListener {
            onPanicReset()
            true
        }

        binding.btnEqual.setOnClickListener {
            onEqualClicked()
        }

        binding.btnEqual.setOnLongClickListener {
            onEqualLongClicked()
        }

        binding.btnPlusMinus.setOnClickListener {
            viewModel.onPlusMinusClick()
            soundManager.playButtonTick()
            hapticManager.buttonTick()
        }

        binding.btnPercent.setOnClickListener {
            viewModel.onPercentClick()
            soundManager.playButtonTick()
            hapticManager.buttonTick()
        }

        binding.btnC.setOnClickListener {
            onClearClicked()
        }

        binding.btnC.setOnLongClickListener {
            onPanicReset()
            true
        }
    }
}
