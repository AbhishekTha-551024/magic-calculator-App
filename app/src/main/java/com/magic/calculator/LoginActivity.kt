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

        // Already logged in → run dynamic verification and skip login screen
        val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (AuthManager.isLoggedIn && user != null) {
            com.magic.calculator.logic.PremiumManager.verifySessionSubscription(this, user.uid) { _ ->
                goToMain()
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
    }

    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("503275768638-o9rh63phatafpkodtm7rvvhkua7eilur.apps.googleusercontent.com")
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
                                com.magic.calculator.logic.PremiumManager.verifySessionSubscription(this@LoginActivity, loggedInUser.uid) { _ ->
                                    showLoading(false)
                                    goToMain()
                                }
                            } else {
                                showLoading(false)
                                goToMain()
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

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
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