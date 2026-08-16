package com.magic.calculator

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.magic.calculator.databinding.ActivitySignupBinding
import com.magic.calculator.logic.AuthManager
import com.magic.calculator.logic.PremiumManager
import kotlinx.coroutines.launch

class SignupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignupBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnCreateAccount.setOnClickListener {
            handleSignup()
        }

        binding.tvLoginLink.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
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

        setLoading(true)
        lifecycleScope.launch {
            val result = AuthManager.registerWithEmail(email, password)
            result.fold(
                onSuccess = {
                    val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                    if (user != null) {
                        PremiumManager.verifySessionSubscription(this@SignupActivity, user.uid) { _ ->
                            setLoading(false)
                            goToMain()
                        }
                    } else {
                        setLoading(false)
                        showError("Account created, but login failed. Please try again.")
                    }
                },
                onFailure = {
                    setLoading(false)
                    showError(it.message ?: "Signup failed. Please try again.")
                }
            )
        }
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
