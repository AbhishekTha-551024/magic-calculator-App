package com.magic.calculator.feature.dialer.presentation

import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.magic.calculator.databinding.ActivityCallingBinding

class CallingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCallingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCallingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val number = intent.getStringExtra("NUMBER") ?: "Unknown"
        binding.tvCallingNumber.text = number

        setupEdgeToEdge()
        
        binding.btnHangup.setOnClickListener {
            finish()
        }
    }

    private fun setupEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController?.isAppearanceLightStatusBars = false
    }
}
