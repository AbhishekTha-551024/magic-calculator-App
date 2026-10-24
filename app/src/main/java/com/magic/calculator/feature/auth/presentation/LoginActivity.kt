package com.magic.calculator.feature.auth.presentation

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.magic.calculator.MainActivity
import com.magic.calculator.R
import com.magic.calculator.databinding.ActivityLoginBinding
import com.magic.calculator.di.appContainer
import com.magic.calculator.feature.auth.presentation.state.AuthUiState
import com.magic.calculator.feature.auth.presentation.viewmodel.AuthViewModel
import com.magic.calculator.core.ui.util.MagicToast
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var googleSignInClient: GoogleSignInClient

    private val viewModel: AuthViewModel by viewModels {
        AuthViewModel.Factory(appContainer.authRepository)
    }

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken ?: run {
                showLoading(false)
                showError("Sign-in incomplete. Please try again.")
                return@registerForActivityResult
            }
            viewModel.loginWithGoogle(idToken)
        } catch (e: ApiException) {
            showLoading(false)
            e.printStackTrace()
            val reason = if (!isInternetAvailable() || e.statusCode == 7) {
                "ðŸ“¶ Network Unavailable â€¢ Please turn on Mobile Data or Wi-Fi to sign in."
            } else when (e.statusCode) {
                10 -> "âš™ï¸ Developer Configuration Syncing â€¢ Please retry in a moment."
                12500 -> "Google Sign-In canceled or Play Services needs update."
                else -> "ðŸ“¶ Network is not good. Please check your data connection."
            }
            showError(reason)
            MagicToast.show(this, reason)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Already logged in â€” check for updates first, then restore premium, then go to main
        if (viewModel.isLoggedIn) {
            com.magic.calculator.core.logic.AppUpdateChecker.checkForUpdate(this) {
                // No update needed (or error) â€” proceed with premium restore
                com.magic.calculator.core.logic.SubscriptionManager.ensureUserDocumentCreated()
                com.magic.calculator.core.logic.SubscriptionManager.syncAndRestorePremiumStatus(this) { _, _ ->
                    goToMain(premiumRestored = true)
                }
            }
            return
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupGoogleSignIn()
        observeViewModel()

        binding.btnGoogleSignIn.setOnClickListener {
            if (!isInternetAvailable()) {
                val netMsg = "ðŸ“¶ No Internet Connection â€¢ Please turn on Mobile Data or Wi-Fi"
                showError(netMsg)
                MagicToast.show(this, netMsg)
                return@setOnClickListener
            }
            showLoading(true)
            binding.tvError.visibility = View.GONE
            googleSignInClient.signOut().addOnCompleteListener {
                googleSignInLauncher.launch(googleSignInClient.signInIntent)
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is AuthUiState.Idle -> {
                            showLoading(false)
                        }
                        is AuthUiState.Loading -> {
                            showLoading(true)
                        }
                        is AuthUiState.Success -> {
                            com.magic.calculator.core.logic.AppUpdateChecker.checkForUpdate(this@LoginActivity) {
                                com.magic.calculator.core.logic.SubscriptionManager.ensureUserDocumentCreated()
                                com.magic.calculator.core.logic.OnboardingManager.syncFromFirestore(this@LoginActivity)
                                com.magic.calculator.core.logic.SubscriptionManager.syncAndRestorePremiumStatus(this@LoginActivity) { _, _ ->
                                    showLoading(false)
                                    goToMain(premiumRestored = true)
                                }
                            }
                        }
                        is AuthUiState.Error -> {
                            showLoading(false)
                            val msg = if (!isInternetAvailable()) {
                                "ðŸ“¶ No Internet Connection â€¢ Please turn on Mobile Data or Wi-Fi"
                            } else {
                                "ðŸ“¶ Network timeout â€¢ Please check your Mobile Data and try again."
                            }
                            showError(msg)
                            MagicToast.show(this@LoginActivity, msg)
                        }
                    }
                }
            }
        }
    }

    private fun isInternetAvailable(): Boolean {
        return try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            val network = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (e: Exception) {
            true // Fallback to let system attempt
        }
    }

    private fun setupGoogleSignIn() {
        val webClientId = try {
            getString(R.string.default_web_client_id)
        } catch (e: Exception) {
            com.magic.calculator.BuildConfig.GOOGLE_OAUTH_CLIENT_ID
        }

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
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
