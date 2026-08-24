package com.magic.calculator

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.magic.calculator.databinding.ActivityLoginBinding
import com.magic.calculator.logic.AuthManager
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var googleSignInClient: GoogleSignInClient

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken ?: run {
                showLoading(false)
                showError("Sign-in failed. Please try again.")
                return@registerForActivityResult
            }
            lifecycleScope.launch {
                val authResult = AuthManager.loginWithGoogle(idToken)
                authResult.fold(
                    onSuccess = {
                        val loggedInUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                        if (loggedInUser != null) {
                            com.magic.calculator.logic.AppUpdateChecker.checkForUpdate(this@LoginActivity) {
                                com.magic.calculator.logic.SubscriptionManager.ensureUserDocumentCreated()
                                com.magic.calculator.logic.OnboardingManager.syncFromFirestore(this@LoginActivity)
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
            googleSignInLauncher.launch(googleSignInClient.signInIntent)
        }
    }

    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(com.magic.calculator.BuildConfig.GOOGLE_OAUTH_CLIENT_ID)
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)
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
        binding.btnGoogleSignIn.alpha = if (show) 0.6f else 1.0f
    }

    private fun showError(message: String) {
        binding.tvError.text = message
        binding.tvError.visibility = View.VISIBLE
    }
}