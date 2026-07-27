package com.magic.calculator.ui.moilock

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.magic.calculator.R

class MoiLockUnlockActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_moilock_unlock)

        val pin = intent.getStringExtra("UNLOCKED_PIN") ?: "53645"
        val tvPinDisplay = findViewById<TextView>(R.id.tvPinDisplay)

        // Highlight the last digit in Gold
        val spannable = SpannableString("Your PIN: $pin")
        val goldColor = Color.parseColor("#FFD700") // Gold
        
        // "Your PIN: " is 10 chars. + length of PIN - 1 for the last digit
        val startHighlight = 10 + pin.length - 1
        val endHighlight = 10 + pin.length
        
        spannable.setSpan(
            ForegroundColorSpan(goldColor),
            startHighlight,
            endHighlight,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        
        tvPinDisplay.text = spannable

        findViewById<Button>(R.id.btnLockAgain).setOnClickListener {
            startActivity(Intent(this, MoiLockActivity::class.java))
            finish()
        }
    }
}
