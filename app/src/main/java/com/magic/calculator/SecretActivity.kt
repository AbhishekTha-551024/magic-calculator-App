package com.magic.calculator

import kotlinx.coroutines.*
import androidx.lifecycle.lifecycleScope
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.magic.calculator.databinding.ActivitySecretBinding
import com.magic.calculator.logic.AuthManager
import com.magic.calculator.logic.SettingsManager
import com.magic.calculator.logic.PremiumManager
import kotlin.math.sqrt

class SecretActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var binding: ActivitySecretBinding
    private lateinit var settingsManager: SettingsManager
    private lateinit var sensorManager: SensorManager
    
    private val sensorViews = mutableMapOf<String, TextView>()
    
    // Virtual Counters
    private var volumeUpCount = 0
    private var volumeDownCount = 0
    private var isAudioRunning = false
    
    // Audio Testing
    private var audioRecord: AudioRecord? = null
    private var audioJob: kotlinx.coroutines.Job? = null
    private val RECORD_AUDIO_PERMISSION_CODE = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySecretBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settingsManager = SettingsManager(this)
        if (settingsManager.appMode == 3 || settingsManager.appMode == 2) {
            settingsManager.appMode = 0
        }
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        
        initUI()
        setupSensorLab()
        setupNavigation()
        setupMagicSwitches()
        setupSensorTriggerSwitches()
        setupSaveButtons()
        
        checkAudioPermission()
    }

    override fun onResume() {
        super.onResume()
        checkPermissionsWarningState()
        // Refresh lock/unlock badges on advanced config cards every time screen is visible
        updateAdvancedCardPremiumBadges()
    }

    private fun initUI() {
        binding.sbSensitivity.progress = settingsManager.shakeSensitivity.toInt()
        binding.sbVolume.progress = settingsManager.soundVolume
        binding.sbHaptic.progress = settingsManager.hapticIntensity
        
        binding.etForcedPhone.setText(settingsManager.forcedPhoneNumber)
        binding.etFlipTrigger.setText(settingsManager.customFlipTrigger)
        binding.etFlipResult.setText(settingsManager.customFlipResult)
        
        // App Identity Mode
        when (settingsManager.appMode) {
            0 -> binding.rbMagicCalc.isChecked = true
            1 -> binding.rbDialer.isChecked = true
            2 -> {
                settingsManager.appMode = 0
                binding.rbMagicCalc.isChecked = true
            }
            3 -> binding.rbGallery.isChecked = true
            4 -> binding.rbMagicCalc.isChecked = true // MoiLock runs alongside standard calculator
        }

        setupAppModeListener()

        val pinManager = com.magic.calculator.logic.PinManager(this)

        val wallpaperPickerLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                try {
                    val inputStream = contentResolver.openInputStream(uri)
                    val outputFile = java.io.File(filesDir, "moilock_wallpaper.jpg")
                    val outputStream = java.io.FileOutputStream(outputFile)
                    inputStream?.copyTo(outputStream)
                    inputStream?.close()
                    outputStream.close()
                    pinManager.customWallpaperPath = outputFile.absolutePath
                    Toast.makeText(this, "Custom Wallpaper Saved!", Toast.LENGTH_SHORT).show()
                } catch (e: java.lang.Exception) {
                    e.printStackTrace()
                    Toast.makeText(this, "Failed to save wallpaper", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val btnProfile = findViewById<android.widget.ImageView>(R.id.btnProfile)
        btnProfile?.setOnClickListener {
            showProfilePanelDialog()
        }

        updateMoiLockIconStatus()
        updateToxicGalleryIconStatus()
        updateP2PIconStatus()
        updateStepRevealIconStatus()

        // List item configurations click listeners
        findViewById<android.view.View>(R.id.cardP2PLinkSetting)?.setOnClickListener {
            checkPremiumAndRun { showP2PLinkPanelDialog() }
        }
        findViewById<android.view.View>(R.id.cardToxicGallerySetting)?.setOnClickListener {
            checkPremiumAndRun { showToxicGalleryPanelDialog() }
        }
        findViewById<android.view.View>(R.id.cardStepRevealSetting)?.setOnClickListener {
            checkPremiumAndRun { showStepRevealPanelDialog() }
        }
        findViewById<android.view.View>(R.id.cardMoiLockSetting)?.setOnClickListener {
            checkPremiumAndRun { showMoiLockPanelDialog(wallpaperPickerLauncher) }
        }
        findViewById<android.view.View>(R.id.cardExplosionSetting)?.setOnClickListener {
            checkPremiumAndRun { showExplosionPanelDialog() }
        }
        updateExplosionIconStatus()

        // Contact Force config card (Advanced Magic Configurations section)
        findViewById<android.view.View>(R.id.cardContactForceSetting)?.setOnClickListener {
            checkPremiumAndRun { showContactForceConfigDialog() }
        }

        // Info button on the Phone Number Force card opens the full performer guide
        findViewById<android.view.View>(R.id.btnPhoneInfo)?.setOnClickListener {
            showContactForceGuideDialog()
        }

        // Magic States — sync all switches from saved state
        applyActiveMagicUI(settingsManager.activeMagic)

        // Toxic Mode Selection
        when (settingsManager.toxicMode) {
            0 -> binding.rbToxicTime.isChecked = true
            1 -> binding.rbToxicDate.isChecked = true
            2 -> binding.rbToxicBattery.isChecked = true
        }

        binding.etGhostNumber.setText(settingsManager.ghostTypingNumber)
        binding.etGhostDelay.setText(settingsManager.ghostTypingDelay.toString())

        // Expanded Sensor Trigger States
        val trig = settingsManager.activeSensorTrigger
        binding.swTrigMagnetic.isChecked = (trig == "MAGNETIC")
        binding.swTrigShake.isChecked = (trig == "SHAKE")
        binding.swTrigTilt.isChecked = (trig == "TILT")
        binding.swTrigFaceDown.isChecked = (trig == "FACE_DOWN")
        binding.swTrigGyro.isChecked = (trig == "GYRO")
        binding.swTrigVolUp.isChecked = (trig == "VOL_UP")
        binding.swTrigVolDown.isChecked = (trig == "VOL_DOWN")
        binding.swTrigClap.isChecked = (trig == "CLAP")
        binding.swTrigFingerprint.isChecked = (trig == "FINGERPRINT")
        binding.swTrigRotate5.isChecked = (trig == "ROTATION_3")

        binding.btnEditParams.setOnClickListener {
            hideAllTabs()
            binding.tabEdit.visibility = View.VISIBLE
            binding.tvTabTitle.text = "Edit Parameters"
        }
        binding.btnSettings.setOnClickListener { binding.tabSettingsOverlay.visibility = View.VISIBLE }
        binding.btnCloseSettings.setOnClickListener {
            saveSystemSettings()
            binding.tabSettingsOverlay.visibility = View.GONE
        }
        setupSimpleMagicCardClicks()
        setupSensorHelpListeners()
        updateAdvancedCardPremiumBadges()
    }

    private fun setupAppModeListener() {
        binding.rgAppMode.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == binding.rbGallery.id && !PremiumManager.isUserPremium) {
                // Temporarily detach listener to avoid recursion
                binding.rgAppMode.setOnCheckedChangeListener(null)
                when (settingsManager.appMode) {
                    0 -> binding.rbMagicCalc.isChecked = true
                    1 -> binding.rbDialer.isChecked = true
                    else -> binding.rbMagicCalc.isChecked = true
                }
                setupAppModeListener() // Re-attach
                
                // Open paywall directly
                val intent = android.content.Intent(this, PremiumActivity::class.java)
                startActivity(intent)
                Toast.makeText(this, "Premium required for Gallery mode!", Toast.LENGTH_SHORT).show()
                return@setOnCheckedChangeListener
            }

            when (checkedId) {
                binding.rbMagicCalc.id -> settingsManager.appMode = 0
                binding.rbDialer.id -> settingsManager.appMode = 1
                binding.rbGallery.id -> settingsManager.appMode = 3
            }
            Toast.makeText(this, "App Mode Updated", Toast.LENGTH_SHORT).show()
            updateMoiLockIconStatus()
            
            // Redirect immediately
            val intent = android.content.Intent(this, MainActivity::class.java)
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
        }
    }

    /**
     * Magics that DON'T need a sensor trigger — they fire automatically on "=" press.
     */
    private fun magicNeedsSensor(magicKey: String): Boolean {
        return magicKey in listOf("DIGIT_FLIP", "GHOST_TYPING", "REAPPEARING_RESULT", "STEP_REVEAL")
    }

    /**
     * Central function: activate ONE magic, deactivate all others, update sensor section.
     * Call with "NONE" to deactivate everything.
     */
    private fun activateMagic(key: String) {
        settingsManager.activeMagic = key
        settingsManager.isContactForceEnabled = (key == "CONTACT_FORCE")

        // Silently sync all switches without triggering their listeners
        val allSwitches = listOf(
            binding.swFlip to "DIGIT_FLIP",
            binding.swLucky to "LUCKY",
            binding.swPhone to "PHONE_NUMBER_FORCE",
            binding.swToxic to "TOXIC_TIME",
            binding.swGhost to "GHOST_TYPING",
            binding.swRecovery to "REAPPEARING_RESULT"
        )
        for ((sw, magicId) in allSwitches) {
            if (sw.isChecked != (magicId == key)) {
                sw.setOnCheckedChangeListener(null)   // detach temporarily
                sw.isChecked = (magicId == key)
                // re-attach will be done in setupMagicSwitches() — already registered
            }
        }
        // Re-register listeners after silent sync
        setupMagicSwitches()

        // Show/hide sensor trigger section based on active magic
        val sensorSection = findViewById<android.view.View>(R.id.layoutSensorTriggerSection)
        if (magicNeedsSensor(key)) {
            sensorSection?.visibility = android.view.View.VISIBLE
            sensorSection?.alpha = 1.0f
        } else {
            sensorSection?.visibility = android.view.View.GONE
            // Also clear active sensor trigger to avoid ghost-firing
            if (key == "NONE") {
                // keep trigger intact so user's setting is remembered
            } else {
                settingsManager.activeSensorTrigger = "NONE"
                clearAllTriggerSwitchesSilently()
            }
        }

        // Update all colored icon/text statuses
        updateToxicGalleryIconStatus()
        updateMoiLockIconStatus()
        updateP2PIconStatus()
        updateStepRevealIconStatus()
        updateExplosionIconStatus()
        updateContactForceIconStatus()
        updateSimpleMagicUIColors()
    }

    /** Syncs the UI switches to match what's saved, then shows/hides sensor section. */
    private fun applyActiveMagicUI(key: String) {
        val allSwitches = listOf(
            binding.swFlip to "DIGIT_FLIP",
            binding.swLucky to "LUCKY",
            binding.swPhone to "PHONE_NUMBER_FORCE",
            binding.swToxic to "TOXIC_TIME",
            binding.swGhost to "GHOST_TYPING",
            binding.swRecovery to "REAPPEARING_RESULT"
        )
        for ((sw, magicId) in allSwitches) {
            sw.setOnCheckedChangeListener(null)
            sw.isChecked = (magicId == key)
        }
        setupMagicSwitches()

        val sensorSection = findViewById<android.view.View>(R.id.layoutSensorTriggerSection)
        if (magicNeedsSensor(key)) {
            sensorSection?.visibility = android.view.View.VISIBLE
            sensorSection?.alpha = 1.0f
        } else {
            sensorSection?.visibility = android.view.View.GONE
        }
        updateSimpleMagicUIColors()
    }

    /** Silently unchecks all trigger sensor switches without firing listeners. */
    private fun clearAllTriggerSwitchesSilently() {
        val trigSwitches = listOf(
            binding.swTrigProximity, binding.swTrigLight, binding.swTrigMagnetic,
            binding.swTrigShake, binding.swTrigTilt, binding.swTrigFaceDown,
            binding.swTrigGyro, binding.swTrigVolUp, binding.swTrigVolDown,
            binding.swTrigClap, binding.swTrigFingerprint, binding.swTrigRotate5
        )
        for (sw in trigSwitches) {
            sw.setOnCheckedChangeListener(null)
            sw.isChecked = false
        }
        setupSensorTriggerSwitches()
    }

    @Suppress("UNUSED_PARAMETER")
    private fun updateTriggerUIState(disable: Boolean) {
        // Legacy stub — now handled by activateMagic() → sensor section show/hide
        // Kept to avoid breaking any remaining references
    }

    private fun checkPremiumAndActivate(magicKey: String, sw: com.google.android.material.switchmaterial.SwitchMaterial) {
        if (PremiumManager.isUserPremium) {
            activateMagic(magicKey)
        } else {
            sw.setOnCheckedChangeListener(null)
            sw.isChecked = false
            setupMagicSwitches()
            
            // Open Premium Paywall
            val intent = android.content.Intent(this, PremiumActivity::class.java)
            startActivity(intent)
            Toast.makeText(this, "Premium membership required for advanced magic features!", Toast.LENGTH_LONG).show()
        }
    }

    private fun checkPremiumAndRun(action: () -> Unit) {
        if (PremiumManager.isUserPremium) {
            action()
        } else {
            // Show the premium paywall — PremiumActivity handles all messaging
            startActivity(android.content.Intent(this, PremiumActivity::class.java))
        }
    }



    private fun showPremiumUpsellDialog(featureName: String) {
        val intent = android.content.Intent(this, PremiumActivity::class.java)
        startActivity(intent)
    }

    private fun setupMagicSwitches() {
        binding.swFlip.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) activateMagic("DIGIT_FLIP")
            else if (settingsManager.activeMagic == "DIGIT_FLIP") activateMagic("NONE")
        }
        binding.swLucky.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) activateMagic("LUCKY")
            else if (settingsManager.activeMagic == "LUCKY") activateMagic("NONE")
        }
        binding.swPhone.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) activateMagic("PHONE_NUMBER_FORCE")
            else if (settingsManager.activeMagic == "PHONE_NUMBER_FORCE") activateMagic("NONE")
        }
        binding.swToxic.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) activateMagic("TOXIC_TIME")
            else if (settingsManager.activeMagic == "TOXIC_TIME") activateMagic("NONE")
        }
        binding.swGhost.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) activateMagic("GHOST_TYPING")
            else if (settingsManager.activeMagic == "GHOST_TYPING") activateMagic("NONE")
        }
        binding.swRecovery.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) activateMagic("REAPPEARING_RESULT")
            else if (settingsManager.activeMagic == "REAPPEARING_RESULT") activateMagic("NONE")
        }
    }

    private fun setupSensorTriggerSwitches() {
        val switches = mapOf(
            binding.swTrigProximity to "PROXIMITY",
            binding.swTrigLight to "LIGHT",
            binding.swTrigMagnetic to "MAGNETIC",
            binding.swTrigShake to "SHAKE",
            binding.swTrigTilt to "TILT",
            binding.swTrigFaceDown to "FACE_DOWN",
            binding.swTrigGyro to "GYRO",
            binding.swTrigVolUp to "VOL_UP",
            binding.swTrigVolDown to "VOL_DOWN",
            binding.swTrigClap to "CLAP",
            binding.swTrigFingerprint to "FINGERPRINT",
            binding.swTrigRotate5 to "ROTATION_3"
        )

        for ((sw, type) in switches) {
            sw.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    updateSensorTrigger(type)
                } else if (settingsManager.activeSensorTrigger == type) {
                    updateSensorTrigger("NONE")
                }
            }
        }
    }

    private fun updateSensorTrigger(type: String) {
        settingsManager.activeSensorTrigger = type
        if (type != "NONE") {
            // Uncheck all except active
            if (type != "PROXIMITY") binding.swTrigProximity.isChecked = false
            if (type != "LIGHT") binding.swTrigLight.isChecked = false
            if (type != "MAGNETIC") binding.swTrigMagnetic.isChecked = false
            if (type != "SHAKE") binding.swTrigShake.isChecked = false
            if (type != "TILT") binding.swTrigTilt.isChecked = false
            if (type != "FACE_DOWN") binding.swTrigFaceDown.isChecked = false
            if (type != "GYRO") binding.swTrigGyro.isChecked = false
            if (type != "VOL_UP") binding.swTrigVolUp.isChecked = false
            if (type != "VOL_DOWN") binding.swTrigVolDown.isChecked = false
            if (type != "CLAP") binding.swTrigClap.isChecked = false
            if (type != "FINGERPRINT") binding.swTrigFingerprint.isChecked = false
            if (type != "ROTATION_3") binding.swTrigRotate5.isChecked = false
        }
    }

    private fun setupSimpleMagicCardClicks() {
        binding.cardFlip.setOnClickListener {
            showPerformanceGuideDialog(
                "Digital Flip",
                listOf(
                    "1. Configure your Trigger (e.g. 666) and Result (e.g. 999) under the 'Edit Parameters' tab.",
                    "2. During performance, enter your Trigger number (666) on the main calculator dialer.",
                    "3. Press the '=' key to show it normally.",
                    "4. Perform your selected sensor trigger secretly (e.g., Wave hand over screen or Tilt phone).",
                    "5. The display will magically flip and instantly update into your forced Result (999)!"
                ),
                "#00E5FF"
            )
        }
        binding.cardLucky.setOnClickListener {
            showPerformanceGuideDialog(
                "Lucky Number Force",
                listOf(
                    "1. Type '777' on the main calculator dialer to open the quick-select Lucky Number screen.",
                    "2. Quickly force any number 1-6 in any order using key shortcuts:\n   - 1: Proximity Wave\n   - 2: Face Down\n   - 3: Volume UP\n   - 4: Volume DOWN\n   - 5: Light Shadow\n   - 6: Shake Phone",
                    "3. Or configure a single persistent Lucky Number in 'Edit Parameters'.",
                    "4. Pressing '=' on any equation will force the Lucky Number to be computed instead of the actual math!"
                ),
                "#FF2D55"
            )
        }
        binding.cardPhone.setOnClickListener {
            showPerformanceGuideDialog(
                "Phone Number Force",
                listOf(
                    "1. Enter a valid Phone Number or target digit sequence in 'Edit Parameters'.",
                    "2. Type a regular calculation in front of the spectator.",
                    "3. Press '=' to execute. The display will instantly change to your saved forced Phone Number!",
                    "4. You can then tap the secret call button to instantly dial the forced contact for an ultimate mind-reading finish!"
                ),
                "#00FF66"
            )
        }
        binding.cardToxic.setOnClickListener {
            showPerformanceGuideDialog(
                "Toxic Time Force",
                listOf(
                    "1. Go to 'Edit Parameters' and choose your active Toxic mode:\n   - Time Mode: Outputs the current clock time (e.g., 10:25).\n   - Date Mode: Outputs today's calendar date.\n   - Battery Mode: Outputs the exact battery percentage.",
                    "2. Perform any mathematical calculation for the spectator.",
                    "3. The moment '=' is pressed, the calculated result will magically match the system status (Time, Date, or Battery) exactly!"
                ),
                "#A855F7"
            )
        }
        binding.cardGhost.setOnClickListener {
            showPerformanceGuideDialog(
                "Ghost Typing Reveal",
                listOf(
                    "1. Configure your target Ghost Number sequence in 'Edit Parameters'.",
                    "2. During performance, place your hand or finger near the proximity sensor or use any active trigger.",
                    "3. After a brief custom delay, the calculator will magically begin typing the exact digits of your ghost number on the display by itself, letter-by-letter, as if a spirit is pressing the buttons!"
                ),
                "#FFCC00"
            )
        }
        binding.cardRecovery.setOnClickListener {
            showPerformanceGuideDialog(
                "Ghost Recovery",
                listOf(
                    "1. Complete a normal calculation in front of the spectator and press '=' to show the result.",
                    "2. Press '=' again secretly to store the value in recovery.",
                    "3. Clear the screen entirely or delete the digits one by one.",
                    "4. Wave your hand over the proximity sensor (or activate your trigger) to magically make the vanished result reappear out of thin air!"
                ),
                "#FF9100"
            )
        }
    }

    private fun setupSensorHelpListeners() {
        binding.helpProximity.setOnClickListener { showHelp("Wave hand over top of phone to trigger.") }
        binding.helpLight.setOnClickListener { showHelp("Cover the light sensor at the top secretly (create a shadow) to trigger.") }
        binding.helpMagnetic.setOnClickListener { showHelp("Bring a magnet/magnetic ring near top of phone.") }
        binding.helpShake.setOnClickListener { showHelp("Shake phone 3 times vertically to trigger.") }
        binding.helpTilt.setOnClickListener { showHelp("Tilt phone quickly to the left/right.") }
        binding.helpFaceDown.setOnClickListener { showHelp("Lay phone screen-down on the table.") }
        binding.helpGyro.setOnClickListener { showHelp("Spin the phone on the table to trigger.") }
        binding.helpVolUp.setOnClickListener { showHelp("Press Volume UP button in your pocket.") }
        binding.helpVolDown.setOnClickListener { showHelp("Press Volume DOWN button in your pocket.") }
        binding.helpClap.setOnClickListener { showHelp("Clap or Snap your fingers near the mic.") }
        binding.helpFingerprint.setOnClickListener { showHelp("Touch the fingerprint sensor secretly.") }
        binding.helpRotate5.setOnClickListener { showHelp("Fully flip the phone over (180°) 3 times to trigger.") }
    }

    private fun updateSimpleMagicUIColors() {
        val active = settingsManager.activeMagic
        
        val simpleMagics = listOf(
            Triple("DIGIT_FLIP", binding.imgFlipIconLeft, binding.txtFlipTitleLeft),
            Triple("LUCKY", binding.imgLuckyIconLeft, binding.txtLuckyTitleLeft),
            Triple("PHONE_NUMBER_FORCE", binding.imgPhoneIconLeft, binding.txtPhoneTitleLeft),
            Triple("TOXIC_TIME", binding.imgToxicIconLeft, binding.txtToxicTitleLeft),
            Triple("GHOST_TYPING", binding.imgGhostIconLeft, binding.txtGhostTitleLeft),
            Triple("REAPPEARING_RESULT", binding.imgRecoveryIconLeft, binding.txtRecoveryTitleLeft)
        )
        
        val activeColors = mapOf(
            "DIGIT_FLIP" to "#00E5FF",       // Cyan
            "LUCKY" to "#FF2D55",            // Rose Red
            "PHONE_NUMBER_FORCE" to "#00FF66", // Glow Green
            "TOXIC_TIME" to "#A855F7",        // Purple
            "GHOST_TYPING" to "#FFCC00",      // Gold
            "REAPPEARING_RESULT" to "#FF9100"  // Amber Orange
        )
        
        for ((magicKey, img, txt) in simpleMagics) {
            if (active == magicKey) {
                val colorHex = activeColors[magicKey] ?: "#88FFFFFF"
                val colorVal = Color.parseColor(colorHex)
                img.setColorFilter(colorVal)
                txt.setTextColor(colorVal)
            } else {
                val dimmedColor = Color.parseColor("#88FFFFFF")
                img.setColorFilter(dimmedColor)
                txt.setTextColor(dimmedColor)
            }
        }
    }

    private fun showPerformanceGuideDialog(title: String, steps: List<String>, activeColorHex: String) {
        val dialog = android.app.Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_performance_guide)
        
        val window = dialog.window
        if (window != null) {
            window.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            window.setDimAmount(0.85f)
        }

        val txtTitle = dialog.findViewById<android.widget.TextView>(R.id.guideTitle)
        val containerSteps = dialog.findViewById<android.widget.LinearLayout>(R.id.guideStepsContainer)
        val btnClose = dialog.findViewById<android.widget.Button>(R.id.guideBtnClose)
        
        txtTitle.text = title
        txtTitle.setTextColor(Color.parseColor(activeColorHex))
        
        containerSteps.removeAllViews()
        for (step in steps) {
            val tv = android.widget.TextView(this).apply {
                text = step
                setTextColor(Color.WHITE)
                textSize = 15f
                setPadding(0, 8, 0, 16)
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            containerSteps.addView(tv)
        }
        
        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showHelp(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    private fun setupSaveButtons() {
        binding.btnSavePhone.setOnClickListener {
            settingsManager.forcedPhoneNumber = binding.etForcedPhone.text.toString()
            Toast.makeText(this, "Phone Magic Saved!", Toast.LENGTH_SHORT).show()
        }
        binding.btnSaveFlip.setOnClickListener {
            settingsManager.customFlipTrigger = binding.etFlipTrigger.text.toString()
            settingsManager.customFlipResult = binding.etFlipResult.text.toString()
            Toast.makeText(this, "Digital Flip Saved!", Toast.LENGTH_SHORT).show()
        }
        binding.btnSaveToxic.setOnClickListener {
            val mode = when (binding.rgToxicMode.checkedRadioButtonId) {
                R.id.rbToxicTime -> 0
                R.id.rbToxicDate -> 1
                R.id.rbToxicBattery -> 2
                else -> 0
            }
            settingsManager.toxicMode = mode
            Toast.makeText(this, "Toxic Mode Saved!", Toast.LENGTH_SHORT).show()
        }
        binding.btnSaveGhost.setOnClickListener {
            settingsManager.ghostTypingNumber = binding.etGhostNumber.text.toString()
            settingsManager.ghostTypingDelay = binding.etGhostDelay.text.toString().toIntOrNull() ?: 5
            Toast.makeText(this, "Ghost Settings Saved!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupNavigation() {

        binding.bottomNav.setOnItemSelectedListener { item ->
            hideAllTabs()
            when (item.itemId) {
                R.id.nav_magic -> {
                    binding.tabMagic.visibility = View.VISIBLE
                    binding.tvTabTitle.text = "Magic List"
                }
                R.id.nav_theme -> {
                    binding.tabTheme.visibility = View.VISIBLE
                    binding.tvTabTitle.text = "Theme"
                }
                R.id.nav_back -> {
                    saveSystemSettings()
                    val intent = android.content.Intent(this, MainActivity::class.java)
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    finish()
                }
            }
            true
        }
    }

    private fun hideAllTabs() {
        binding.tabMagic.visibility = View.GONE
        binding.tabEdit.visibility = View.GONE
        binding.tabTheme.visibility = View.GONE
        binding.tabSettingsOverlay.visibility = View.GONE
    }

    private fun saveSystemSettings() {
        settingsManager.shakeSensitivity = binding.sbSensitivity.progress.toFloat()
        settingsManager.soundVolume = binding.sbVolume.progress
        settingsManager.hapticIntensity = binding.sbHaptic.progress
    }

    private fun setupSensorLab() {
        val sensorsToTest = listOf(
            Sensor.TYPE_PROXIMITY to "Proximity (Wave Trick)",
            Sensor.TYPE_LIGHT to "Light (Shadow Trick)",
            Sensor.TYPE_MAGNETIC_FIELD to "Magnetometer (Magnetic Ring)",
            Sensor.TYPE_ACCELEROMETER to "Accelerometer (Shake/Tilt/Flip)",
            Sensor.TYPE_GYROSCOPE to "Gyroscope (Spin Trick)",
            Sensor.TYPE_PRESSURE to "Barometer (Blow Trick)"
        )

        for ((type, name) in sensorsToTest) {
            val sensor = sensorManager.getDefaultSensor(type)
            addSensorView(type.toString(), name, sensor != null)
            if (sensor != null) {
                sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
            }
        }
        
        addVirtualTriggerView("VOL_UP", "Volume UP Button")
        addVirtualTriggerView("VOL_DOWN", "Volume DOWN Button")
        addVirtualTriggerView("FACE_DOWN", "Orientation (Face Down)")
        addVirtualTriggerView("CLAP", "Microphone (Clap/Snap)")
        addVirtualTriggerView("FINGERPRINT", "Fingerprint Sensor")
    }

    private fun addSensorView(id: String, name: String, available: Boolean) {
        val card = LayoutInflater.from(this).inflate(R.layout.item_sensor_test, binding.sensorListContainer, false)
        val tvName = card.findViewById<TextView>(R.id.tvSensorName)
        val tvStatus = card.findViewById<TextView>(R.id.tvSensorStatus)
        val tvValue = card.findViewById<TextView>(R.id.tvSensorValue)

        tvName.text = name
        if (available) {
            tvStatus.text = "ONLINE"
            tvStatus.setTextColor(getSafeColor(R.color.dialer_green))
            sensorViews[id] = tvValue
        } else {
            tvStatus.text = "NOT FOUND"
            tvStatus.setTextColor(Color.RED)
            tvValue.text = "Missing"
        }
        binding.sensorListContainer.addView(card)
    }

    private fun addVirtualTriggerView(id: String, name: String) {
        val card = LayoutInflater.from(this).inflate(R.layout.item_sensor_test, binding.sensorListContainer, false)
        val tvName = card.findViewById<TextView>(R.id.tvSensorName)
        val tvStatus = card.findViewById<TextView>(R.id.tvSensorStatus)
        val tvValue = card.findViewById<TextView>(R.id.tvSensorValue)

        tvName.text = name
        tvStatus.text = "READY"
        tvStatus.setTextColor(Color.CYAN)
        sensorViews[id] = tvValue
        binding.sensorListContainer.addView(card)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val tvValue = sensorViews[event.sensor.type.toString()]
        
        when (event.sensor.type) {
            Sensor.TYPE_PROXIMITY -> {
                tvValue?.text = if (event.values[0] < event.sensor.maximumRange) "NEAR (Hand detected!)" else "FAR"
            }
            Sensor.TYPE_LIGHT -> {
                tvValue?.text = "Light Level: ${event.values[0]} lux"
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                val strength = sqrt((event.values[0] * event.values[0] + event.values[1] * event.values[1] + event.values[2] * event.values[2]).toDouble())
                tvValue?.text = "Mag Field: ${String.format("%.1f", strength)} uT"
            }
            Sensor.TYPE_ACCELEROMETER -> {
                val z = event.values[2]
                if (z < -8.5) {
                    sensorViews["FACE_DOWN"]?.text = "FACE DOWN (Trick Active!)"
                    sensorViews["FACE_DOWN"]?.setTextColor(Color.YELLOW)
                } else {
                    sensorViews["FACE_DOWN"]?.text = "Normal"
                    sensorViews["FACE_DOWN"]?.setTextColor(Color.GRAY)
                }
                tvValue?.text = "X: ${String.format("%.1f", event.values[0])}, Y: ${String.format("%.1f", event.values[1])}, Z: ${String.format("%.1f", z)}"
            }
            else -> {
                tvValue?.text = "Val: ${event.values[0]}"
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                volumeUpCount++
                sensorViews["VOL_UP"]?.text = "Pressed: $volumeUpCount times"
                return true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                volumeDownCount++
                sensorViews["VOL_DOWN"]?.text = "Pressed: $volumeDownCount times"
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun checkAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), RECORD_AUDIO_PERMISSION_CODE)
        } else {
            startAudioTesting()
        }
    }

    private fun startAudioTesting() {
        isAudioRunning = true
        audioJob?.cancel()
        audioJob = lifecycleScope.launch(Dispatchers.Default) {
            try {
                val bufferSize = AudioRecord.getMinBufferSize(44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                val record = AudioRecord(MediaRecorder.AudioSource.MIC, 44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize)
                audioRecord = record
                val buffer = ShortArray(bufferSize)
                record.startRecording()

                while (isAudioRunning && isActive) {
                    val read = record.read(buffer, 0, bufferSize)
                    if (read > 0) {
                        var maxAmplitude = 0
                        for (i in 0 until read) {
                            val value = Math.abs(buffer[i].toInt())
                            if (value > maxAmplitude) {
                                maxAmplitude = value
                            }
                        }
                        
                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                            val tvClap = sensorViews["CLAP"]
                            if (maxAmplitude > 15000) {
                                tvClap?.text = "LOUD (Clap/Snap detected!)"
                                tvClap?.setTextColor(Color.RED)
                            } else {
                                tvClap?.text = "Quiet (Level: $maxAmplitude)"
                                tvClap?.setTextColor(Color.GRAY)
                            }
                        }
                    }
                    kotlinx.coroutines.delay(100)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try {
                    audioRecord?.stop()
                    audioRecord?.release()
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
                audioRecord = null
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RECORD_AUDIO_PERMISSION_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startAudioTesting()
        } else if (requestCode == 200 || requestCode == 201 || requestCode == 203) {
            // Microphone (200) / Contacts (201) / Call Log (203) permission result
            val allGranted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                val name = when (requestCode) {
                    201 -> "Contacts"
                    203 -> "Call Log"
                    else -> "Microphone"
                }
                Toast.makeText(this, "✓ $name permission granted!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    this,
                    "Permission denied. Tap Enable again to open Settings and grant manually.",
                    Toast.LENGTH_LONG
                ).show()
            }
            // Always re-evaluate so rows hide when fixed
            checkPermissionsWarningState()
        }
    }

    override fun onPause() {
        super.onPause()
        saveSystemSettings()
        isAudioRunning = false
        audioJob?.cancel()
        audioJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        audioRecord = null
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        isAudioRunning = false
        audioJob?.cancel()
        audioJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        audioRecord = null
    }
    
    private fun getSafeColor(id: Int): Int = ContextCompat.getColor(this, id)

    private fun checkAndRequestMoiLockPermissions() {
        var needsPermission = false

        // Request Device Admin
        val devicePolicyManager = getSystemService(android.content.Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
        val componentName = android.content.ComponentName(this, com.magic.calculator.ui.moilock.MoiDeviceAdminReceiver::class.java)
        if (!devicePolicyManager.isAdminActive(componentName)) {
            val intent = android.content.Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            intent.putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
            intent.putExtra(android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Required for MoiLock to simulate realistic lock screen and block Home button.")
            startActivity(intent)
            needsPermission = true
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(this)) {
            // Request Overlay Permission only if Admin is already granted (to prevent intent conflict)
            val intent = android.content.Intent(
                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:$packageName")
            )
            startActivity(intent)
            needsPermission = true
        }

        if (needsPermission) {
            Toast.makeText(this, "Please grant the requested permission to use MoiLock.", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "All required MoiLock permissions are already granted!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateMoiLockIconStatus() {
        val imgMoiLockIconLeft = findViewById<android.widget.ImageView>(R.id.imgMoiLockIconLeft) ?: return
        val txtMoiLockTitleLeft = findViewById<android.widget.TextView>(R.id.txtMoiLockTitleLeft) ?: return
        if (settingsManager.appMode == 4) {
            imgMoiLockIconLeft.setColorFilter(Color.parseColor("#A855F7")) // Purple / Active!
            txtMoiLockTitleLeft.setTextColor(Color.parseColor("#A855F7"))
        } else {
            imgMoiLockIconLeft.setColorFilter(Color.parseColor("#88FFFFFF")) // Elegant dimmed white/grey
            txtMoiLockTitleLeft.setTextColor(Color.parseColor("#88FFFFFF"))
        }
    }

    private fun showMoiLockPanelDialog(wallpaperPickerLauncher: androidx.activity.result.ActivityResultLauncher<String>) {
        val dialog = android.app.Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_moilock_settings)
        
        val window = dialog.window
        if (window != null) {
            window.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            window.setDimAmount(0.85f)
        }

        val pinManager = com.magic.calculator.logic.PinManager(this)

        // Initialize views
        val swMoiLockEnable = dialog.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.swMoiLockEnable)
        val etSecret = dialog.findViewById<android.widget.EditText>(R.id.dialogEtMoiLockSecret)
        val btnSaveSecret = dialog.findViewById<android.widget.Button>(R.id.dialogBtnSaveMoiLockSecret)
        val etMaster = dialog.findViewById<android.widget.EditText>(R.id.dialogEtMoiLockMasterPin)
        val btnSaveMaster = dialog.findViewById<android.widget.Button>(R.id.dialogBtnSaveMoiLockMaster)
        val btnSelectWallpaper = dialog.findViewById<android.widget.Button>(R.id.dialogBtnSelectMoiLockWallpaper)
        val btnBatterySaver = dialog.findViewById<android.widget.Button>(R.id.dialogBtnBatterySaver)
        val btnLaunchMoiLock = dialog.findViewById<android.widget.Button>(R.id.dialogBtnLaunchMoiLock)
        val btnClose = dialog.findViewById<android.widget.ImageView>(R.id.btnCloseDialog)
        val tvPermStatus = dialog.findViewById<android.widget.TextView>(R.id.tvPermissionsStatus)

        btnBatterySaver.setOnClickListener {
            try {
                val intent = android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                startActivity(intent)
                Toast.makeText(this, "Select 'All Apps', find Magic Calculator, and set to 'No Restrictions' or 'Unrestricted'!", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Please disable battery restrictions for the app in system settings.", Toast.LENGTH_LONG).show()
            }
        }

        // Set status/values
        swMoiLockEnable.isChecked = (settingsManager.appMode == 4)
        etSecret.setText(pinManager.secretDigit)
        etMaster.setText(pinManager.masterPin)

        // Update permissions text helper
        val updatePermText = {
            val devicePolicyManager = getSystemService(android.content.Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
            val componentName = android.content.ComponentName(this, com.magic.calculator.ui.moilock.MoiDeviceAdminReceiver::class.java)
            val adminActive = devicePolicyManager.isAdminActive(componentName)
            val overlayActive = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                android.provider.Settings.canDrawOverlays(this)
            } else true

            var permText = "Permissions Status:\n"
            permText += if (adminActive) "✓ Device Admin Allowed\n" else "✗ Device Admin Missing\n"
            permText += if (overlayActive) "✓ Overlay Allowed" else "✗ Overlay Missing"
            tvPermStatus.text = permText
        }
        
        updatePermText()

        // Handlers
        btnClose.setOnClickListener { dialog.dismiss() }

        swMoiLockEnable.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Try enabling and request permission if missing
                var needsPermission = false
                val devicePolicyManager = getSystemService(android.content.Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
                val componentName = android.content.ComponentName(this, com.magic.calculator.ui.moilock.MoiDeviceAdminReceiver::class.java)
                if (!devicePolicyManager.isAdminActive(componentName)) {
                    val intent = android.content.Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                    intent.putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
                    intent.putExtra(android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Required to simulate realistic lock screen and block Home button.")
                    startActivity(intent)
                    needsPermission = true
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(this)) {
                    val intent = android.content.Intent(
                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                    needsPermission = true
                }

                settingsManager.appMode = 4
                val serviceIntent = android.content.Intent(this, com.magic.calculator.ui.moilock.MoiLockService::class.java)
                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        startForegroundService(serviceIntent)
                    } else {
                        startService(serviceIntent)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                if (needsPermission) {
                    Toast.makeText(this, "Please grant permissions to fully activate MoiLock!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "MoiLock Enabled & Service Started!", Toast.LENGTH_SHORT).show()
                }
            } else {
                settingsManager.appMode = 0 // Standard Magic Calculator
                val serviceIntent = android.content.Intent(this, com.magic.calculator.ui.moilock.MoiLockService::class.java)
                try {
                    stopService(serviceIntent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                Toast.makeText(this, "MoiLock Disabled & Service Stopped!", Toast.LENGTH_SHORT).show()
            }
            updateMoiLockIconStatus()
            updatePermText()
        }

        btnSaveSecret.setOnClickListener {
            val digit = etSecret.text.toString()
            if (digit.length == 1) {
                pinManager.secretDigit = digit
                pinManager.resetLock()
                Toast.makeText(this, "MoiLock Digit Saved & Trick Reset!", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Must be exactly 1 digit!", Toast.LENGTH_SHORT).show()
            }
        }

        btnSaveMaster.setOnClickListener {
            val master = etMaster.text.toString()
            if (master.length == 5) {
                pinManager.masterPin = master
                Toast.makeText(this, "Master PIN Saved!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Master PIN must be 5 digits!", Toast.LENGTH_SHORT).show()
            }
        }

        btnSelectWallpaper.setOnClickListener {
            wallpaperPickerLauncher.launch("image/*")
            dialog.dismiss() // Dismiss so they can pick, they can re-open to check
        }

        btnLaunchMoiLock.setOnClickListener {
            val intent = android.content.Intent(this, com.magic.calculator.ui.moilock.MoiLockActivity::class.java)
            startActivity(intent)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun updateToxicGalleryIconStatus() {
        val imgGalleryIconLeft = findViewById<android.widget.ImageView>(R.id.imgGalleryIconLeft) ?: return
        val txtGalleryTitleLeft = findViewById<android.widget.TextView>(R.id.txtGalleryTitleLeft) ?: return
        if (settingsManager.activeMagic == "TOXIC_GALLERY") {
            imgGalleryIconLeft.setColorFilter(Color.parseColor("#FF2D55")) // Rose Red / Active!
            txtGalleryTitleLeft.setTextColor(Color.parseColor("#FF2D55"))
        } else {
            imgGalleryIconLeft.setColorFilter(Color.parseColor("#88FFFFFF")) // Elegant dimmed white/grey
            txtGalleryTitleLeft.setTextColor(Color.parseColor("#88FFFFFF"))
        }
    }

    private fun showToxicGalleryPanelDialog() {
        val dialog = android.app.Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_toxic_gallery_settings)
        
        val window = dialog.window
        if (window != null) {
            window.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            window.setDimAmount(0.85f)
        }

        val swToxicGalleryEnable = dialog.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.swToxicGalleryEnable)
        val etForcedTime = dialog.findViewById<android.widget.EditText>(R.id.dialogEtForcedTime)
        val btnSaveForcedTime = dialog.findViewById<android.widget.Button>(R.id.dialogBtnSaveForcedTime)
        val btnLaunchGallery = dialog.findViewById<android.widget.Button>(R.id.dialogBtnLaunchGallery)
        val btnClose = dialog.findViewById<android.widget.ImageView>(R.id.btnCloseGalleryDialog)

        // Bind current states
        swToxicGalleryEnable.isChecked = (settingsManager.activeMagic == "TOXIC_GALLERY")
        etForcedTime.setText(settingsManager.forcedGalleryTime)

        btnClose.setOnClickListener { dialog.dismiss() }

        swToxicGalleryEnable.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) activateMagic("TOXIC_GALLERY")
            else if (settingsManager.activeMagic == "TOXIC_GALLERY") activateMagic("NONE")
        }

        btnSaveForcedTime.setOnClickListener {
            val forcedTime = etForcedTime.text.toString().trim()
            if (forcedTime.isNotEmpty()) {
                var formatted = forcedTime.replace(".", ":")
                if (!formatted.contains(":")) {
                    Toast.makeText(this, "Please enter a valid time like 4:20 or 4.20", Toast.LENGTH_SHORT).show()
                } else {
                    settingsManager.forcedGalleryTime = formatted
                    Toast.makeText(this, "Forced Time Saved: $formatted", Toast.LENGTH_SHORT).show()
                }
            } else {
                settingsManager.forcedGalleryTime = "4:20"
                Toast.makeText(this, "Reset to default", Toast.LENGTH_SHORT).show()
            }
        }

        btnLaunchGallery.setOnClickListener {
            dialog.dismiss()
            settingsManager.appMode = 3 // Gallery mode
            val intent = android.content.Intent(this, com.magic.calculator.ui.GalleryActivity::class.java)
            startActivity(intent)
        }

        dialog.show()
    }

    private fun updateExplosionIconStatus() {
        val imgExplosionIconLeft = findViewById<android.widget.ImageView>(R.id.imgExplosionIconLeft) ?: return
        val txtExplosionTitleLeft = findViewById<android.widget.TextView>(R.id.txtExplosionTitleLeft) ?: return
        if (settingsManager.isExplosionEnabled) {
            imgExplosionIconLeft.setColorFilter(Color.parseColor("#FF9100")) // Neon Orange/Amber / Active!
            txtExplosionTitleLeft.setTextColor(Color.parseColor("#FF9100"))
        } else {
            imgExplosionIconLeft.setColorFilter(Color.parseColor("#88FFFFFF")) // Elegant dimmed white/grey
            txtExplosionTitleLeft.setTextColor(Color.parseColor("#88FFFFFF"))
        }
    }

    private fun showExplosionPanelDialog() {
        val dialog = android.app.Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_explosion_settings)
        
        val window = dialog.window
        if (window != null) {
            window.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            window.setDimAmount(0.85f)
        }

        val swExplosionEnable = dialog.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.dialogSwExplosionEnable)
        val rgExplosionTrigger = dialog.findViewById<android.widget.RadioGroup>(R.id.dialogRgExplosionTrigger)
        val rbExplosionProximity = dialog.findViewById<android.widget.RadioButton>(R.id.dialogRbExplosionProximity)
        val rbExplosionMagnetic = dialog.findViewById<android.widget.RadioButton>(R.id.dialogRbExplosionMagnetic)
        val btnClose = dialog.findViewById<android.widget.ImageView>(R.id.btnCloseExplosionDialog)

        swExplosionEnable.isChecked = settingsManager.isExplosionEnabled
        if (settingsManager.explosionTrigger == "PROXIMITY") {
            rbExplosionProximity.isChecked = true
        } else {
            rbExplosionMagnetic.isChecked = true
        }

        btnClose.setOnClickListener { dialog.dismiss() }

        swExplosionEnable.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.isExplosionEnabled = isChecked
            updateExplosionIconStatus()
        }

        rgExplosionTrigger.setOnCheckedChangeListener { _, checkedId ->
            val trigger = if (checkedId == R.id.dialogRbExplosionProximity) "PROXIMITY" else "MAGNETIC"
            settingsManager.explosionTrigger = trigger
        }

        dialog.show()
    }

    private fun updateP2PIconStatus() {
        val imgIcon = findViewById<android.widget.ImageView>(R.id.imgP2PLinkIconLeft) ?: return
        val txtTitle = findViewById<android.widget.TextView>(R.id.txtP2PLinkTitleLeft) ?: return
        val p2p = com.magic.calculator.p2p.P2PLinkManager.getInstance()
        if (p2p.isConnected) {
            imgIcon.setColorFilter(Color.parseColor("#00E5FF"))
            txtTitle.setTextColor(Color.parseColor("#00E5FF"))
        } else {
            imgIcon.setColorFilter(Color.parseColor("#88FFFFFF"))
            txtTitle.setTextColor(Color.parseColor("#88FFFFFF"))
        }
    }

    private fun showP2PLinkPanelDialog() {
        val dialog = android.app.Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_p2p_link_settings)

        val window = dialog.window
        if (window != null) {
            window.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            window.setDimAmount(0.87f)
        }

        val p2p = com.magic.calculator.p2p.P2PLinkManager.getInstance()

        // Bind dialog views
        val swP2PEnable = dialog.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.swP2PEnable)
        val layoutP2PContent = dialog.findViewById<android.widget.LinearLayout>(R.id.layoutP2PContent)
        val tvMyMagicId = dialog.findViewById<android.widget.TextView>(R.id.tvMyMagicId)
        val btnCopyMagicId = dialog.findViewById<android.widget.ImageView>(R.id.btnCopyMagicId)
        val etPartnerMagicId = dialog.findViewById<android.widget.EditText>(R.id.etPartnerMagicId)
        val btnSendRequest = dialog.findViewById<android.widget.Button>(R.id.btnSendRequest)
        val tvP2PStatus = dialog.findViewById<android.widget.TextView>(R.id.tvP2PStatus)
        val cardIncomingRequest = dialog.findViewById<android.view.View>(R.id.cardIncomingRequest)
        val tvIncomingRequestFrom = dialog.findViewById<android.widget.TextView>(R.id.tvIncomingRequestFrom)
        val btnAcceptRequest = dialog.findViewById<android.widget.Button>(R.id.btnAcceptRequest)
        val btnDeclineRequest = dialog.findViewById<android.widget.Button>(R.id.btnDeclineRequest)
        val layoutMirrorModeRow = dialog.findViewById<android.view.View>(R.id.layoutMirrorModeRow)
        val swMirrorMode = dialog.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.swMirrorMode)
        val btnDisconnect = dialog.findViewById<android.widget.Button>(R.id.btnDisconnect)
        val cardRemoteTriggers = dialog.findViewById<android.view.View>(R.id.cardRemoteTriggers)
        val btnP2PTriggerFlip = dialog.findViewById<android.widget.Button>(R.id.btnP2PTriggerFlip)
        val btnP2PTriggerExplosion = dialog.findViewById<android.widget.Button>(R.id.btnP2PTriggerExplosion)
        val btnCloseDialog = dialog.findViewById<android.widget.ImageView>(R.id.btnCloseP2PDialog)

        // Sync initial state of master switch
        swP2PEnable.isChecked = settingsManager.isP2PEnabled
        layoutP2PContent.visibility = if (settingsManager.isP2PEnabled) android.view.View.VISIBLE else android.view.View.GONE

        swP2PEnable.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                settingsManager.isP2PEnabled = true
                layoutP2PContent.visibility = android.view.View.VISIBLE
                if (p2p.myMagicId.isEmpty() && com.magic.calculator.logic.AuthManager.currentUid != null) {
                    tvMyMagicId.text = "Setting up..."
                    p2p.initialize { magicId ->
                        runOnUiThread { tvMyMagicId.text = magicId }
                        p2p.listenForAcceptedRequest { _, partnerMagicId ->
                            runOnUiThread {
                                tvP2PStatus.text = "🟢 Connected to $partnerMagicId"
                                tvP2PStatus.setTextColor(Color.parseColor("#00FF66"))
                                layoutMirrorModeRow.visibility = android.view.View.VISIBLE
                                btnDisconnect.visibility = android.view.View.VISIBLE
                                cardRemoteTriggers.visibility = android.view.View.VISIBLE
                                updateP2PIconStatus()
                                settingsManager.p2pPartnerMagicId = partnerMagicId
                            }
                        }
                    }
                } else if (p2p.myMagicId.isNotEmpty()) {
                    p2p.startListeningForRequests()
                }
            } else {
                settingsManager.isP2PEnabled = false
                layoutP2PContent.visibility = android.view.View.GONE
                p2p.disconnect() // Also disconnects active connections
                updateP2PIconStatus()
            }
        }

        // Pending incoming request tracker
        var pendingRequestDocId = ""
        var pendingRequestFromId = ""

        // Helper to update status text
        val updateStatus = { isConnected: Boolean, partner: String?, extra: String? ->
            if (isConnected && partner != null) {
                tvP2PStatus.text = "🟢 Connected to $partner"
                tvP2PStatus.setTextColor(Color.parseColor("#00FF66"))
                layoutMirrorModeRow.visibility = android.view.View.VISIBLE
                btnDisconnect.visibility = android.view.View.VISIBLE
                cardRemoteTriggers.visibility = android.view.View.VISIBLE
            } else if (extra != null) {
                tvP2PStatus.text = "🟡 $extra"
                tvP2PStatus.setTextColor(Color.parseColor("#FFCC00"))
                layoutMirrorModeRow.visibility = android.view.View.GONE
                btnDisconnect.visibility = android.view.View.GONE
                cardRemoteTriggers.visibility = android.view.View.GONE
            } else {
                tvP2PStatus.text = "🔴 Not Connected"
                tvP2PStatus.setTextColor(Color.parseColor("#FF4444"))
                layoutMirrorModeRow.visibility = android.view.View.GONE
                btnDisconnect.visibility = android.view.View.GONE
                cardRemoteTriggers.visibility = android.view.View.GONE
            }
        }

        // Initialize Magic ID text
        if (p2p.myMagicId.isNotEmpty()) {
            tvMyMagicId.text = p2p.myMagicId
        } else if (settingsManager.isP2PEnabled) {
            tvMyMagicId.text = "Setting up..."
            p2p.initialize { magicId ->
                runOnUiThread { tvMyMagicId.text = magicId }
                // Also start listening for accepted requests (MASTER side)
                p2p.listenForAcceptedRequest { _, partnerMagicId ->
                    runOnUiThread {
                        updateStatus(true, partnerMagicId, null)
                        updateP2PIconStatus()
                        settingsManager.isP2PEnabled = true
                        settingsManager.p2pPartnerMagicId = partnerMagicId
                    }
                }
            }
        }

        // Refresh connection status
        if (p2p.isConnected) {
            updateStatus(true, p2p.partnerMagicId, null)
            swMirrorMode.isChecked = p2p.isMirrorEnabled
        } else {
            updateStatus(false, null, null)
        }

        // Set live P2P event listener for this dialog
        p2p.listener = object : com.magic.calculator.p2p.P2PLinkManager.P2PEventListener {
            override fun onIncomingRequest(fromMagicId: String, requestDocId: String) {
                runOnUiThread {
                    pendingRequestDocId = requestDocId
                    pendingRequestFromId = fromMagicId
                    tvIncomingRequestFrom.text = "Connection request from $fromMagicId"
                    cardIncomingRequest.visibility = android.view.View.VISIBLE
                }
            }

            override fun onConnected(partnerMagicId: String, sessionId: String) {
                runOnUiThread {
                    updateStatus(true, partnerMagicId, null)
                    updateP2PIconStatus()
                    cardIncomingRequest.visibility = android.view.View.GONE
                    settingsManager.isP2PEnabled = true
                    settingsManager.p2pSessionId = sessionId
                    settingsManager.p2pPartnerMagicId = partnerMagicId
                }
            }

            override fun onDisconnected() {
                runOnUiThread {
                    updateStatus(false, null, null)
                    updateP2PIconStatus()
                    swMirrorMode.isChecked = false
                    settingsManager.isP2PEnabled = false
                    settingsManager.p2pSessionId = ""
                    settingsManager.p2pPartnerMagicId = ""
                }
            }

            override fun onRemoteDisplayUpdate(display: String, formula: String) { /* handled in MainActivity */ }
            override fun onRemoteCommand(command: String) { /* handled in MainActivity */ }
            override fun onError(message: String) {
                runOnUiThread { Toast.makeText(this@SecretActivity, message, Toast.LENGTH_LONG).show() }
            }
        }

        // Copy Magic ID to clipboard
        btnCopyMagicId.setOnClickListener {
            val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Magic ID", p2p.myMagicId)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Magic ID '${p2p.myMagicId}' copied!", Toast.LENGTH_SHORT).show()
        }

        // Send connection request
        btnSendRequest.setOnClickListener {
            val targetId = etPartnerMagicId.text.toString().trim()
            if (targetId.isEmpty()) {
                Toast.makeText(this, "Enter your partner's Magic ID.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            updateStatus(false, null, "Sending request...")
            p2p.sendRequest(targetId) { success, message ->
                runOnUiThread {
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    if (success) {
                        updateStatus(false, null, "Waiting for acceptance...")
                        // Start listening for the partner's acceptance
                        p2p.listenForAcceptedRequest { sessionId, partnerMagicId ->
                            runOnUiThread {
                                updateStatus(true, partnerMagicId, null)
                                updateP2PIconStatus()
                                settingsManager.isP2PEnabled = true
                                settingsManager.p2pSessionId = sessionId
                                settingsManager.p2pPartnerMagicId = partnerMagicId
                            }
                        }
                    } else {
                        updateStatus(false, null, null)
                    }
                }
            }
        }

        // Accept incoming request
        btnAcceptRequest.setOnClickListener {
            if (pendingRequestDocId.isNotEmpty()) {
                updateStatus(false, null, "Connecting...")
                p2p.acceptRequest(pendingRequestDocId, pendingRequestFromId)
                cardIncomingRequest.visibility = android.view.View.GONE
            }
        }

        // Decline incoming request
        btnDeclineRequest.setOnClickListener {
            if (pendingRequestDocId.isNotEmpty()) {
                p2p.declineRequest(pendingRequestDocId)
                cardIncomingRequest.visibility = android.view.View.GONE
                pendingRequestDocId = ""
                pendingRequestFromId = ""
                Toast.makeText(this, "Request declined.", Toast.LENGTH_SHORT).show()
            }
        }

        // Mirror mode toggle
        swMirrorMode.isChecked = p2p.isMirrorEnabled
        swMirrorMode.setOnCheckedChangeListener { _, isChecked ->
            p2p.setMirrorEnabled(isChecked)
            settingsManager.isP2PMirrorEnabled = isChecked
        }

        // Disconnect button
        btnDisconnect.setOnClickListener {
            p2p.disconnect()
        }

        // Remote trigger: Flip
        btnP2PTriggerFlip.setOnClickListener {
            if (p2p.isConnected) {
                p2p.sendCommand("TRG:FLIP")
                Toast.makeText(this, "Flip trigger sent to partner!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Not connected to any partner.", Toast.LENGTH_SHORT).show()
            }
        }

        // Remote trigger: Explosion
        btnP2PTriggerExplosion.setOnClickListener {
            if (p2p.isConnected) {
                p2p.sendCommand("TRG:EXPL")
                Toast.makeText(this, "Explosion trigger sent to partner!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Not connected to any partner.", Toast.LENGTH_SHORT).show()
            }
        }

        btnCloseDialog.setOnClickListener {
            // Keep P2P alive; only detach dialog-specific listener, MainActivity takes over
            p2p.listener = null
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun updateStepRevealIconStatus() {
        val imgStepRevealSettingIconLeft = findViewById<android.widget.ImageView>(R.id.imgStepRevealSettingIconLeft) ?: return
        val txtStepRevealSettingTitleLeft = findViewById<android.widget.TextView>(R.id.txtStepRevealSettingTitleLeft) ?: return
        if (settingsManager.activeMagic == "STEP_REVEAL") {
            imgStepRevealSettingIconLeft.setColorFilter(Color.parseColor("#00E5FF")) // Active Cyan!
            txtStepRevealSettingTitleLeft.setTextColor(Color.parseColor("#00E5FF"))
        } else {
            imgStepRevealSettingIconLeft.setColorFilter(Color.parseColor("#88FFFFFF")) // Elegant dimmed white/grey
            txtStepRevealSettingTitleLeft.setTextColor(Color.parseColor("#88FFFFFF"))
        }
    }

    private fun showStepRevealPanelDialog() {
        val dialog = android.app.Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_step_reveal_settings)
        
        val window = dialog.window
        if (window != null) {
            window.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            window.setDimAmount(0.85f)
        }

        val swStepRevealEnable = dialog.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.swStepRevealEnable)
        val etStepSequence = dialog.findViewById<android.widget.EditText>(R.id.dialogEtStepSequence)
        val btnSaveStepSequence = dialog.findViewById<android.widget.Button>(R.id.dialogBtnSaveStepSequence)
        val btnClose = dialog.findViewById<android.widget.ImageView>(R.id.btnCloseStepRevealDialog)

        // Bind current states
        swStepRevealEnable.isChecked = (settingsManager.activeMagic == "STEP_REVEAL")
        etStepSequence.setText(settingsManager.stepRevealSequence)

        btnClose.setOnClickListener { dialog.dismiss() }

        swStepRevealEnable.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) activateMagic("STEP_REVEAL")
            else if (settingsManager.activeMagic == "STEP_REVEAL") activateMagic("NONE")
        }

        btnSaveStepSequence.setOnClickListener {
            val seq = etStepSequence.text.toString().trim()
            if (seq.isNotEmpty()) {
                val cleaned = seq.split(",").map { it.trim().uppercase() }
                val validTriggers = setOf("TOUCH", "PROXIMITY", "FACE_DOWN", "LIGHT", "MAGNETIC", "TILT", "GYRO", "VOL_UP", "VOL_DOWN", "CLAP", "FINGERPRINT", "ROTATION_3", "ANY")
                val invalid = cleaned.filter { it !in validTriggers }
                if (invalid.isNotEmpty()) {
                    Toast.makeText(this, "Invalid triggers: $invalid. Use TOUCH, PROXIMITY, FACE_DOWN, LIGHT, etc.", Toast.LENGTH_LONG).show()
                } else {
                    settingsManager.stepRevealSequence = cleaned.joinToString(",")
                    Toast.makeText(this, "Sequence saved: ${settingsManager.stepRevealSequence}", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Sequence cannot be empty.", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    private fun getDaysRemaining(expiryTimestamp: Long): String {
        val currentTime = System.currentTimeMillis()
        val difference = expiryTimestamp - currentTime
        if (difference <= 0) {
            val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            if (user != null) {
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val userRef = db.collection("users").document(user.uid)
                val expiredData = mapOf(
                    "isPremium" to false,
                    "purchaseType" to "free",
                    "premiumExpiryDate" to 0L,
                    "paymentStatus" to "EXPIRED"
                )
                userRef.set(expiredData, com.google.firebase.firestore.SetOptions.merge())
            }
            return "Plan Expired"
        }
        val days = difference / (1000 * 60 * 60 * 24)
        val hours = (difference % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60)
        return "Expires in $days Days, $hours Hours"
    }

    private fun showProfilePanelDialog() {
        val dialog = android.app.Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_profile_settings)

        val window = dialog.window
        if (window != null) {
            window.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            window.setDimAmount(0.85f)
        }

        val profileAvatarInitials = dialog.findViewById<android.widget.TextView>(R.id.profileAvatarInitials)
        val profileTxtEmail = dialog.findViewById<android.widget.TextView>(R.id.profileTxtEmail)
        val cardOverlay = dialog.findViewById<android.view.View>(R.id.profileCardOverlayPermission)
        val cardBackup = dialog.findViewById<android.view.View>(R.id.profileCardCloudBackup)
        val btnLogout = dialog.findViewById<android.widget.Button>(R.id.profileBtnLogout)
        val btnClose = dialog.findViewById<android.widget.ImageView>(R.id.btnCloseProfileDialog)
        val premiumBadge = dialog.findViewById<android.view.View>(R.id.profilePremiumBadge)
        val txtPremiumBadge = dialog.findViewById<android.widget.TextView>(R.id.profileTxtPremiumBadge)

        // Subscription details card views
        val txtProfilePlanStatus = dialog.findViewById<android.widget.TextView>(R.id.txtProfilePlanStatus)
        val txtProfilePurchaseType = dialog.findViewById<android.widget.TextView>(R.id.txtProfilePurchaseType)
        val txtProfilePurchaseDate = dialog.findViewById<android.widget.TextView>(R.id.txtProfilePurchaseDate)
        val txtProfileTransactionId = dialog.findViewById<android.widget.TextView>(R.id.txtProfileTransactionId)
        val txtProfileExpiryDate = dialog.findViewById<android.widget.TextView>(R.id.txtProfileExpiryDate)
        val txtProfileExpiryCountdown = dialog.findViewById<android.widget.TextView>(R.id.txtProfileExpiryCountdown)
        val btnProfileUpgradeCard = dialog.findViewById<android.widget.Button>(R.id.btnProfileUpgradeCard)

        val rowProfilePurchaseType = dialog.findViewById<android.view.View>(R.id.rowProfilePurchaseType)
        val rowProfilePurchaseDate = dialog.findViewById<android.view.View>(R.id.rowProfilePurchaseDate)
        val rowProfileTransactionId = dialog.findViewById<android.view.View>(R.id.rowProfileTransactionId)
        val rowProfileExpiry = dialog.findViewById<android.view.View>(R.id.rowProfileExpiry)

        val btnSimulate7Day = dialog.findViewById<android.widget.Button>(R.id.btnSimulate7Day)
        val btnSimulateLifetime = dialog.findViewById<android.widget.Button>(R.id.btnSimulateLifetime)
        val txtProfileSandboxHeader = dialog.findViewById<android.widget.TextView>(R.id.txtProfileSandboxHeader)
        val layoutProfileSandboxButtons = dialog.findViewById<android.view.View>(R.id.layoutProfileSandboxButtons)

        val email = AuthManager.currentUser?.email ?: "guest@magicapp.com"
        profileTxtEmail.text = email
        if (email.isNotEmpty()) {
            profileAvatarInitials.text = email.take(1).uppercase()
        }

        fun triggerPaywall() {
            dialog.dismiss()
            val intent = android.content.Intent(this, PremiumActivity::class.java)
            startActivity(intent)
        }

        if (PremiumManager.isUserPremium) {
            txtPremiumBadge.text = "PRO MEMBER"
            premiumBadge.setOnClickListener(null)
        } else {
            txtPremiumBadge.text = "UPGRADE TO PRO"
            premiumBadge.setOnClickListener { triggerPaywall() }
        }

        btnProfileUpgradeCard.setOnClickListener { triggerPaywall() }

        // Sandbox simulated purchase logic
        btnSimulate7Day.setOnClickListener {
            btnSimulate7Day.text = "Processing..."
            PremiumManager.updatePremiumFirestore(this, true, "MONTHLY") { success, msg ->
                btnSimulate7Day.text = "+ 7 Days"
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        }

        btnSimulateLifetime.setOnClickListener {
            btnSimulateLifetime.text = "Processing..."
            PremiumManager.updatePremiumFirestore(this, true, "lifetime") { success, msg ->
                btnSimulateLifetime.text = "+ Lifetime"
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        }

        txtProfilePlanStatus.text = "Loading Ledger..."
        txtProfilePlanStatus.setTextColor(Color.parseColor("#88FFFFFF"))
        
        // Listen to live updates on the transactional schema
        val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (user != null) {
            val listenerRegistration = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users")
                .document(user.uid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || !dialog.isShowing) {
                        return@addSnapshotListener
                    }
                    if (snapshot != null && snapshot.exists()) {
                        val isPremium = snapshot.getBoolean("isPremium") ?: false
                        val purchaseType = snapshot.getString("purchaseType") ?: "free"
                        val premiumExpiryDate = snapshot.getLong("premiumExpiryDate") ?: 0L
                        val activatedAt = snapshot.getLong("activatedAt") ?: 0L
                        val transactionId = snapshot.getString("transactionId") ?: "N/A"
                        
                        val dateFormat = java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault())

                        if (isPremium) {
                            txtProfilePlanStatus.text = "CalcMagic Premium Pro"
                            txtProfilePlanStatus.setTextColor(Color.parseColor("#FFD700"))
                            txtPremiumBadge.text = "PRO MEMBER"
                            premiumBadge.setOnClickListener(null)

                            rowProfilePurchaseType.visibility = android.view.View.VISIBLE
                            rowProfilePurchaseDate.visibility = android.view.View.VISIBLE
                            rowProfileTransactionId.visibility = android.view.View.VISIBLE
                            rowProfileExpiry.visibility = android.view.View.VISIBLE
                            btnProfileUpgradeCard.visibility = android.view.View.GONE
                            
                            txtProfileSandboxHeader.visibility = android.view.View.VISIBLE
                            layoutProfileSandboxButtons.visibility = android.view.View.VISIBLE

                            txtProfileTransactionId.text = transactionId

                            if (activatedAt > 0L) {
                                txtProfilePurchaseDate.text = dateFormat.format(java.util.Date(activatedAt))
                            } else {
                                txtProfilePurchaseDate.text = "N/A"
                            }

                            if (purchaseType == "lifetime") {
                                txtProfilePurchaseType.text = "Lifetime License"
                                txtProfileExpiryDate.text = "Never Expires (Lifetime Access)"
                                txtProfileExpiryCountdown.text = "Forever Unlocked"
                                txtProfileExpiryCountdown.setTextColor(Color.parseColor("#00FF66"))
                            } else {
                                txtProfilePurchaseType.text = "Monthly/7-Day Subscription"
                                if (premiumExpiryDate > 0L) {
                                    txtProfileExpiryDate.text = dateFormat.format(java.util.Date(premiumExpiryDate))
                                    val daysStatus = getDaysRemaining(premiumExpiryDate)
                                    txtProfileExpiryCountdown.text = daysStatus
                                    if (daysStatus == "Plan Expired") {
                                        txtProfileExpiryCountdown.setTextColor(Color.parseColor("#FF2D55"))
                                    } else {
                                        txtProfileExpiryCountdown.setTextColor(Color.parseColor("#FFD700"))
                                    }
                                } else {
                                    txtProfileExpiryDate.text = "Active"
                                    txtProfileExpiryCountdown.text = ""
                                }
                            }
                        } else {
                            // Free Tier Reversion
                            txtProfilePlanStatus.text = "CalcMagic Free Tier"
                            txtProfilePlanStatus.setTextColor(Color.parseColor("#88FFFFFF"))
                            txtPremiumBadge.text = "UPGRADE TO PRO"
                            premiumBadge.setOnClickListener { triggerPaywall() }

                            rowProfilePurchaseType.visibility = android.view.View.GONE
                            rowProfilePurchaseDate.visibility = android.view.View.GONE
                            rowProfileTransactionId.visibility = android.view.View.GONE
                            rowProfileExpiry.visibility = android.view.View.GONE
                            btnProfileUpgradeCard.visibility = android.view.View.VISIBLE
                            
                            txtProfileSandboxHeader.visibility = android.view.View.VISIBLE
                            layoutProfileSandboxButtons.visibility = android.view.View.VISIBLE
                        }
                    }
                }
                
            dialog.setOnDismissListener {
                listenerRegistration.remove()
            }
        } else {
            // Unauthenticated
            txtProfilePlanStatus.text = "CalcMagic Free Tier"
            txtProfilePlanStatus.setTextColor(Color.parseColor("#88FFFFFF"))
            rowProfilePurchaseType.visibility = android.view.View.GONE
            rowProfilePurchaseDate.visibility = android.view.View.GONE
            rowProfileTransactionId.visibility = android.view.View.GONE
            rowProfileExpiry.visibility = android.view.View.GONE
            btnProfileUpgradeCard.visibility = android.view.View.VISIBLE
        }

        btnClose.setOnClickListener { dialog.dismiss() }

        cardOverlay.setOnClickListener {
            dialog.dismiss()
            checkAndRequestMoiLockPermissions()
        }

        cardBackup.setOnClickListener {
            Toast.makeText(this, "Configurations Synced to Cloud Securely!", Toast.LENGTH_SHORT).show()
        }

        btnLogout.setOnClickListener {
            dialog.dismiss()
            AuthManager.logout()
            PremiumManager.clearLocalPremium(this)
            val intent = android.content.Intent(this, LoginActivity::class.java)
            intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }

        dialog.show()
    }

    private fun checkPermissionsWarningState() {
        val cardWarning = findViewById<androidx.cardview.widget.CardView>(R.id.cardPermissionWarning) ?: return

        // Helper: open system permission request or app settings if permanently denied
        fun requestRuntime(perm: String, code: Int) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, perm) ||
                ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(perm), code)
            } else {
                // Permanently denied — send to App Settings
                try {
                    startActivity(android.content.Intent(
                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.parse("package:$packageName")
                    ))
                    Toast.makeText(this, "Tap Permissions and enable manually.", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    ActivityCompat.requestPermissions(this, arrayOf(perm), code)
                }
            }
        }

        // --- Overlay row ---
        val rowOverlay = findViewById<android.view.View>(R.id.rowPermOverlay)
        val btnOverlay = findViewById<android.widget.Button>(R.id.btnGrantOverlay)
        val missingOverlay = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M &&
                !android.provider.Settings.canDrawOverlays(this)
        rowOverlay?.visibility = if (missingOverlay) View.VISIBLE else View.GONE
        btnOverlay?.setOnClickListener {
            try {
                startActivity(android.content.Intent(
                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:$packageName")
                ))
                Toast.makeText(this, "Enable 'Display over other apps' then come back.", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                startActivity(android.content.Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:$packageName")
                ))
            }
        }

        // --- Contacts row ---
        val rowContacts = findViewById<android.view.View>(R.id.rowPermContacts)
        val btnContacts = findViewById<android.widget.Button>(R.id.btnGrantContacts)
        val missingContacts = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED
        rowContacts?.visibility = if (missingContacts) View.VISIBLE else View.GONE
        btnContacts?.setOnClickListener { requestRuntime(Manifest.permission.READ_CONTACTS, 201) }

        // --- Call Log row ---
        val rowCallLog = findViewById<android.view.View>(R.id.rowPermCallLog)
        val btnCallLog = findViewById<android.widget.Button>(R.id.btnGrantCallLog)
        val missingCallLog = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED
        rowCallLog?.visibility = if (missingCallLog) View.VISIBLE else View.GONE
        btnCallLog?.setOnClickListener { requestRuntime(Manifest.permission.READ_CALL_LOG, 203) }

        // --- Microphone row ---
        val rowMic = findViewById<android.view.View>(R.id.rowPermMic)
        val btnMic = findViewById<android.widget.Button>(R.id.btnGrantMic)
        val missingMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        rowMic?.visibility = if (missingMic) View.VISIBLE else View.GONE
        btnMic?.setOnClickListener { requestRuntime(Manifest.permission.RECORD_AUDIO, 200) }

        // Show the card only if at least one permission is missing
        val anyMissing = missingOverlay || missingContacts || missingCallLog || missingMic
        cardWarning.visibility = if (anyMissing) View.VISIBLE else View.GONE
    }

    private fun showContactForceGuideDialog() {
        val dialog = android.app.Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_contact_force_guide)

        val window = dialog.window
        if (window != null) {
            window.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            window.setDimAmount(0.85f)
        }

        val btnCloseGuide = dialog.findViewById<android.widget.ImageView>(R.id.btnCloseGuide)
        val btnDismissGuide = dialog.findViewById<android.widget.Button>(R.id.btnDismissGuide)
        val btnTestPredictionView = dialog.findViewById<android.widget.Button>(R.id.btnTestPredictionView)

        btnCloseGuide?.setOnClickListener { dialog.dismiss() }
        btnDismissGuide?.setOnClickListener { dialog.dismiss() }

        btnTestPredictionView?.setOnClickListener {
            dialog.dismiss()
            // 1. Activate magic
            activateMagic("CONTACT_FORCE")
            
            // 2. Load demo contact data if empty
            if (settingsManager.forcedContactName.isEmpty()) {
                settingsManager.forcedContactName = "Abha Gupta"
            }
            if (settingsManager.forcedContactPhone.isEmpty()) {
                settingsManager.forcedContactPhone = "+91 98765 43210"
            }
            
            // 3. Set app mode to 3 (Gallery) so it transitions properly
            settingsManager.appMode = 3

            // 4. Open PhotoViewerActivity targeting prediction_friends.jpg directly
            val intent = android.content.Intent(this, com.magic.calculator.ui.PhotoViewerActivity::class.java).apply {
                putExtra("EXTRA_IMAGE_NAME", "prediction_friends.jpg")
                putExtra("EXTRA_POSITION", 1)
                putExtra("EXTRA_TOTAL", 1)
            }
            startActivity(intent)
            Toast.makeText(this, "Testing Contact Force Overlay View!", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    private fun showContactForceConfigDialog() {
        val dialog = android.app.Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_contact_force_config)

        val window = dialog.window
        if (window != null) {
            window.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            window.setDimAmount(0.85f)
        }

        val swEnable = dialog.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.swEnableContactForce)
        val etName = dialog.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etForcedContactName)
        val etPhone = dialog.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etForcedContactPhone)
        val btnClose = dialog.findViewById<android.view.View>(R.id.btnCloseConfig)
        val btnSave = dialog.findViewById<android.widget.Button>(R.id.btnSaveConfig)
        val btnTest = dialog.findViewById<android.widget.Button>(R.id.btnTestPredictionView)

        // Load current saved values
        swEnable?.isChecked = settingsManager.isContactForceEnabled
        etName?.setText(settingsManager.forcedContactName)
        etPhone?.setText(settingsManager.forcedContactPhone)

        // Update card icon color live when toggled
        swEnable?.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.isContactForceEnabled = isChecked
            val color = if (isChecked) Color.parseColor("#00E5FF") else Color.parseColor("#88FFFFFF")
            findViewById<android.widget.ImageView>(R.id.imgContactForceIcon)?.setColorFilter(color)
            findViewById<android.widget.TextView>(R.id.txtContactForceTitle)?.setTextColor(color)
        }

        btnClose?.setOnClickListener { dialog.dismiss() }

        btnSave?.setOnClickListener {
            val name = etName?.text?.toString()?.trim() ?: ""
            val phone = etPhone?.text?.toString()?.trim() ?: ""
            if (name.isNotEmpty()) settingsManager.forcedContactName = name
            if (phone.isNotEmpty()) settingsManager.forcedContactPhone = phone
            val isChecked = swEnable?.isChecked ?: false

            // If enabling, also set activeMagic so gallery shows the overlay
            if (isChecked) {
                activateMagic("CONTACT_FORCE")
            } else {
                // Only reset if contact force was the active trick
                if (settingsManager.activeMagic == "CONTACT_FORCE") {
                    activateMagic("NONE")
                }
            }

            updateContactForceIconStatus()
            Toast.makeText(this, "Contact Force settings saved ✓", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        btnTest?.setOnClickListener {
            dialog.dismiss()
            // Save current inputs before testing
            val name = etName?.text?.toString()?.trim() ?: ""
            val phone = etPhone?.text?.toString()?.trim() ?: ""
            if (name.isNotEmpty()) settingsManager.forcedContactName = name
            if (phone.isNotEmpty()) settingsManager.forcedContactPhone = phone
            activateMagic("CONTACT_FORCE")
            settingsManager.appMode = 3

            // Demo fallback if still empty
            if (settingsManager.forcedContactName.isEmpty()) settingsManager.forcedContactName = "Abha Gupta"
            if (settingsManager.forcedContactPhone.isEmpty()) settingsManager.forcedContactPhone = "+91 98765 43210"

            val intent = android.content.Intent(this, com.magic.calculator.ui.PhotoViewerActivity::class.java).apply {
                putExtra("EXTRA_IMAGE_NAME", "prediction_friends.jpg")
                putExtra("EXTRA_POSITION", 1)
                putExtra("EXTRA_TOTAL", 1)
            }
            startActivity(intent)
            Toast.makeText(this, "Testing Contact Force Overlay…", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    private fun updateContactForceIconStatus() {
        val enabled = settingsManager.isContactForceEnabled
        val color = if (enabled) Color.parseColor("#00E5FF") else Color.parseColor("#88FFFFFF")
        findViewById<android.widget.ImageView>(R.id.imgContactForceIcon)?.setColorFilter(color)
        findViewById<android.widget.TextView>(R.id.txtContactForceTitle)?.setTextColor(color)
    }

    /**
     * Swaps the right-side icon of every advanced config card:
     *   - Free users  → gold padlock  (ic_lock)
     *   - Premium     → settings cog  (ic_settings)
     * Called on every screen resume so the UI stays in sync after a purchase.
     */
    private fun updateAdvancedCardPremiumBadges() {
        val isPremium = PremiumManager.isUserPremium
        val iconRes = if (isPremium) R.drawable.ic_settings else R.drawable.ic_lock
        val tintColor = if (isPremium) "#88FFFFFF" else "#FFD700"
        val statusIds = listOf(
            R.id.imgContactForceStatus,
            R.id.imgP2PLinkStatus,
            R.id.imgGalleryStatus,
            R.id.imgStepRevealStatus,
            R.id.imgMoiLockStatus,
            R.id.imgExplosionStatus
        )
        for (id in statusIds) {
            val img = findViewById<android.widget.ImageView>(id) ?: continue
            img.setImageResource(iconRes)
            img.setColorFilter(android.graphics.Color.parseColor(tintColor))
        }
    }
}

