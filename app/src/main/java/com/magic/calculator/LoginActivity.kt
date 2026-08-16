package com.magic.calculator

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.magic.calculator.databinding.ActivityLoginBinding
import com.magic.calculator.logic.AuthManager  // ← THIS was missing
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var googleSignInClient: GoogleSignInClient

    companion object {
        private const val RC_GOOGLE_SIGN_IN = 9001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Already logged in — check for updates first, then restore premium, then go to main
        val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (AuthManager.isLoggedIn && user != null) {
            com.magic.calculator.logic.AppUpdateChecker.checkForUpdate(this) {
                // No update needed (or error) — proceed with premium restore
                com.magic.calculator.logic.SubscriptionManager.ensureUserDocumentCreated()
                com.magic.calculator.logic.SubscriptionManager.syncAndRestorePremiumStatus(this) { _, _ ->
                    goToMain(premiumRestored = true)
                }
            }
            return
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupGoogleSignIn()

        binding.btnGoogleSignIn.setOnClickListener {
            showLoading(true)
            val signInIntent = googleSignInClient.signInIntent
            startActivityForResult(signInIntent, RC_GOOGLE_SIGN_IN)
        }

        binding.btnEmailLogin.setOnClickListener {
            handleEmailLogin()
        }

        binding.tvLoginLink.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
            finish()
        }
    }

    private fun handleEmailLogin() {
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

        showLoading(true)
        lifecycleScope.launch {
            val result = AuthManager.loginWithEmail(email, password)
            result.fold(
                onSuccess = {
                    val loggedInUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                    if (loggedInUser != null) {
                        // Check for updates first, then restore premium
                        com.magic.calculator.logic.AppUpdateChecker.checkForUpdate(this@LoginActivity) {
                            com.magic.calculator.logic.SubscriptionManager.ensureUserDocumentCreated()
                            com.magic.calculator.logic.SubscriptionManager.syncAndRestorePremiumStatus(this@LoginActivity) { _, _ ->
                                showLoading(false)
                                goToMain(premiumRestored = true)
                            }
                        }
                    } else {
                        showLoading(false)
                        showError("Login succeeded but user data is unavailable.")
                    }
                },
                onFailure = {
                    showLoading(false)
                    showError(it.message ?: "Login failed. Please try again.")
                }
            )
        }
    }

    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(com.magic.calculator.BuildConfig.GOOGLE_OAUTH_CLIENT_ID)
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    @Deprecated("Using for Google Sign-In result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_GOOGLE_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                val idToken = account.idToken ?: run {
                    showLoading(false)
                    showError("Sign-in failed. Please try again.")
                    return
                }
                lifecycleScope.launch {
                    val result = AuthManager.loginWithGoogle(idToken)
                    result.fold(
                        onSuccess = {
                            val loggedInUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                            if (loggedInUser != null) {
                                // Check for updates first, then restore premium
                                com.magic.calculator.logic.AppUpdateChecker.checkForUpdate(this@LoginActivity) {
                                    com.magic.calculator.logic.SubscriptionManager.ensureUserDocumentCreated()
                                    com.magic.calculator.logic.SubscriptionManager.syncAndRestorePremiumStatus(this@LoginActivity) { _, _ ->
                                        showLoading(false)
                                        goToMain(premiumRestored = true)
                                    }
                                }
                            } else {
                                showLoading(false)
                                goToMain(premiumRestored = false)
                            }
                        },
                        onFailure = { 
                            showLoading(false)
                            showError("Sign-in failed: ${it.message}") 
                        }
                    )
                }
            } catch (e: ApiException) {
                showLoading(false)
                showError("Google Sign-In failed (code ${e.statusCode})")
            }
        }
    }

    private fun goToMain(premiumRestored: Boolean = false) {
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra("premium_already_restored", premiumRestored)
        startActivity(intent)
        finish()
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.btnGoogleSignIn.isEnabled = !show
        binding.btnEmailLogin.isEnabled = !show
        binding.etEmail.isEnabled = !show
        binding.etPassword.isEnabled = !show
        binding.btnGoogleSignIn.alpha = if (show) 0.6f else 1.0f
        binding.btnEmailLogin.alpha = if (show) 0.6f else 1.0f
    }

    private fun showError(message: String) {
        binding.tvError.text = message
        binding.tvError.visibility = View.VISIBLE
    }
}