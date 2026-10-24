package com.magic.calculator.feature.auth.presentation

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.magic.calculator.MainActivity
import com.magic.calculator.databinding.ActivitySignupBinding
import com.magic.calculator.di.appContainer
import com.magic.calculator.feature.auth.presentation.state.AuthUiState
import com.magic.calculator.feature.auth.presentation.viewmodel.AuthViewModel
import com.magic.calculator.core.logic.PremiumManager
import kotlinx.coroutines.launch

class SignupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignupBinding

    private val viewModel: AuthViewModel by viewModels {
        AuthViewModel.Factory(appContainer.authRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        observeViewModel()

        binding.btnCreateAccount.setOnClickListener {
            handleSignup()
        }

        binding.tvLoginLink.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is AuthUiState.Idle -> {
                            setLoading(false)
                        }
                        is AuthUiState.Loading -> {
                            setLoading(true)
                        }
                        is AuthUiState.Success -> {
                            com.magic.calculator.core.logic.SubscriptionManager.ensureUserDocumentCreated()
                            com.magic.calculator.core.logic.OnboardingManager.resetOnboarding(this@SignupActivity, isPro = false)
                            PremiumManager.verifySessionSubscription(this@SignupActivity, state.user.uid) { _ ->
                                setLoading(false)
                                goToMain()
                            }
                        }
                        is AuthUiState.Error -> {
                            setLoading(false)
                            showError(state.message)
                        }
                    }
                }
            }
        }
    }

    private fun handleSignup() {
        val email = binding.etEmail.text?.toString()?.trim() ?: ""
        val password = binding.etPassword.text?.toString() ?: ""

        binding.tvError.visibility = View.GONE

        if (email.isEmpty()) {
            showError("Please enter your email.")
            return
        }
        if (password.length < 6) {
            showError("Password must be at least 6 characters.")
            return
        }

        viewModel.registerWithEmail(email, password)
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnCreateAccount.isEnabled = !isLoading
        binding.etEmail.isEnabled = !isLoading
        binding.etPassword.isEnabled = !isLoading
    }

    private fun showError(message: String) {
        binding.tvError.text = message
        binding.tvError.visibility = View.VISIBLE
    }
}
