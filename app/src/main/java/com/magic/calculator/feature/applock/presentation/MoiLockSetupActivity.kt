package com.magic.calculator.feature.applock.presentation

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import com.magic.calculator.R
import com.magic.calculator.di.appContainer
import com.magic.calculator.core.ui.util.MagicToast

class MoiLockSetupActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_moilock_setup)

        val appLockRepository = appContainer.appLockRepository
        val config = appLockRepository.getLockConfig()

        val etSecretDigit = findViewById<EditText>(R.id.etSecretDigit)
        val etFakeDate = findViewById<EditText>(R.id.etFakeDate)
        
        etSecretDigit.setText(config.secretDigit)
        etFakeDate.setText(config.fakeDate)

        findViewById<Button>(R.id.btnLockSave).setOnClickListener {
            val digit = etSecretDigit.text.toString().trim()
            val date = etFakeDate.text.toString().trim()
            
            if (digit.length == 1) {
                appLockRepository.saveLockConfig(digit, date)
                finish()
            } else {
                MagicToast.show(this, "Secret must be 1 digit")
            }
        }

        findViewById<Button>(R.id.btnReset).setOnClickListener {
            appLockRepository.resetLockTrick()
            MagicToast.show(this, "Trick Reset! Next unlock will capture the PIN.", isLong = true)
            finish()
        }
    }
}
