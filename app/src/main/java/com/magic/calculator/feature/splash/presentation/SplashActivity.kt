package com.magic.calculator.feature.splash.presentation

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.magic.calculator.databinding.ActivitySplashBinding
import com.magic.calculator.feature.auth.presentation.LoginActivity
import com.magic.calculator.MainActivity
import com.magic.calculator.feature.splash.domain.model.SplashDestination
import com.magic.calculator.feature.splash.domain.model.StartupStatus
import com.magic.calculator.feature.splash.presentation.viewmodel.SplashViewModel

/**
 * SplashActivity
 *
 * Visually rich entry point with glowing magic emblem, particle bursts,
 * and automated authentication & update verification.
 */
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private val viewModel: SplashViewModel by viewModels()
    private var isTransitionInitiated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupEdgeToEdge()

        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        startSplashAnimations()
        observeStartupStatus()
        viewModel.performStartup(this)
    }

    private fun setupEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private fun startSplashAnimations() {
        // Initial States
        binding.cvEmblem.scaleX = 0.3f
        binding.cvEmblem.scaleY = 0.3f
        binding.cvEmblem.alpha = 0f

        binding.tvAppTitle.alpha = 0f
        binding.tvAppTitle.translationY = 40f

        binding.layoutBottomStatus.alpha = 0f

        // 1. Emblem Scale & Overshoot Pop-In
        binding.cvEmblem.animate()
            .scaleX(1.0f)
            .scaleY(1.0f)
            .alpha(1.0f)
            .setDuration(900L)
            .setInterpolator(OvershootInterpolator(1.6f))
            .withEndAction {
                // Trigger subtle particle burst
                val loc = IntArray(2)
                binding.cvEmblem.getLocationOnScreen(loc)
                val centerX = loc[0] + binding.cvEmblem.width / 2f
                val centerY = loc[1] + binding.cvEmblem.height / 2f

                binding.particleView.burst(centerX, centerY, Color.parseColor("#00E5FF"))
                binding.particleView.burst(centerX, centerY, Color.parseColor("#A855F7"))
                binding.particleView.burst(centerX, centerY, Color.parseColor("#FFD700"))
            }
            .start()

        // 2. Halo Breathing Glow Animation
        ObjectAnimator.ofFloat(binding.vGlowHalo, View.ALPHA, 0.2f, 0.6f, 0.2f).apply {
            duration = 1800L
            repeatCount = ValueAnimator.INFINITE
            start()
        }

        // 3. Title Slide Up & Fade In
        binding.tvAppTitle.animate()
            .alpha(1.0f)
            .translationY(0f)
            .setStartDelay(300L)
            .setDuration(700L)
            .setInterpolator(DecelerateInterpolator())
            .start()

        // 4. Status Loader Fade In
        binding.layoutBottomStatus.animate()
            .alpha(1.0f)
            .setStartDelay(500L)
            .setDuration(500L)
            .start()
    }

    private fun observeStartupStatus() {
        viewModel.startupStatus.observe(this) { status ->
            when (status) {
                is StartupStatus.Initializing -> {
                    binding.tvStatusMessage.text = "Initializing..."
                }
                is StartupStatus.CheckingUpdates -> {
                    binding.tvStatusMessage.text = status.message
                }
                is StartupStatus.SyncingProfile -> {
                    binding.tvStatusMessage.text = status.message
                }
                is StartupStatus.Ready -> {
                    binding.tvStatusMessage.text = status.message
                    navigateToDestination(status.destination)
                }
            }
        }
    }

    private fun navigateToDestination(destination: SplashDestination) {
        if (isTransitionInitiated) return
        isTransitionInitiated = true

        val targetClass = when (destination) {
            is SplashDestination.Main -> MainActivity::class.java
            is SplashDestination.Login -> LoginActivity::class.java
        }

        val intent = Intent(this, targetClass).apply {
            putExtra("premium_already_restored", true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}
