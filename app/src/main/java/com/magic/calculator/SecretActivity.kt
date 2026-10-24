package com.magic.calculator

import android.Manifest
import android.content.Context
import android.content.Intent
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.magic.calculator.databinding.ActivitySecretBinding
import com.magic.calculator.di.AppContainer
import com.magic.calculator.core.logic.PinManager
import com.magic.calculator.core.logic.PremiumManager
import com.magic.calculator.feature.subscription.presentation.PremiumActivity
import com.magic.calculator.core.logic.SettingsManager
import com.magic.calculator.core.logic.SubscriptionManager
import com.magic.calculator.core.logic.InteractiveCoachManager
import com.magic.calculator.core.logic.AppIconManager
import com.magic.calculator.core.ui.util.MagicToast
import com.magic.calculator.feature.secret.presentation.dialog.SecretDialogHelper
import com.magic.calculator.feature.applock.presentation.service.MoiLockService
import com.magic.calculator.feature.applock.presentation.receiver.MoiDeviceAdminReceiver
import com.magic.calculator.feature.p2p.presentation.P2PLinkManager
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import kotlin.math.sqrt

/**
 * Secret Backdoor Switchboard & Sensor Hardware Lab.
 * Dialog management is delegated to [SecretDialogHelper].
 */
class SecretActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var binding: ActivitySecretBinding
    private val appContainer: AppContainer get() = (application as MagicApplication).appContainer
    private val settingsManager: SettingsManager by lazy { appContainer.settingsManager }
    private lateinit var sensorManager: SensorManager

    private val sensorViews = mutableMapOf<String, TextView>()

    // Virtual Counters
    private var volumeUpCount = 0
    private var volumeDownCount = 0
    private var isAudioRunning = false

    // Audio Testing
    private var audioRecord: AudioRecord? = null
    private var audioJob: Job? = null
    private val RECORD_AUDIO_PERMISSION_CODE = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySecretBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
        applySubscriptionState()
    }

    override fun onResume() {
        super.onResume()
        checkPermissionsWarningState()
        applySubscriptionState()

        val coachCard = binding.layoutSecretCoach.cardCoachOverlay
        val tvTitle = binding.layoutSecretCoach.tvCoachTitle
        val tvInstruction = binding.layoutSecretCoach.tvCoachInstruction
        val btnSkip = binding.layoutSecretCoach.btnCoachSkip
        btnSkip.visibility = View.GONE
        btnSkip.setOnClickListener {
            InteractiveCoachManager.skipTraining(this)
            coachCard.visibility = View.GONE
        }

        InteractiveCoachManager.currentStep.observe(this) { step ->
            if (step == InteractiveCoachManager.TrainingStep.OFF ||
                step == InteractiveCoachManager.TrainingStep.TRAINING_COMPLETED ||
                step == InteractiveCoachManager.TrainingStep.STEP_8_PERFORM_66 ||
                step == InteractiveCoachManager.TrainingStep.STEP_9_PERFORM_EQUAL ||
                step == InteractiveCoachManager.TrainingStep.STEP_10_PERFORM_WAVE) {
                coachCard.visibility = View.GONE
                hideSecretPointer()
                setSecretLockState(null)
            } else {
                coachCard.visibility = View.VISIBLE
                tvTitle.text = step.title
                tvInstruction.text = step.instruction

                when (step) {
                    InteractiveCoachManager.TrainingStep.STEP_5_CONFIG_FLIP -> {
                        setSecretLockState(binding.cardFlip)
                        binding.cardFlip.animate().scaleX(1.04f).scaleY(1.04f).setDuration(300).start()
                        showSecretPointerAbove(binding.swFlip, "👇 Turn ON Digital Flip")
                        binding.tabMagic.smoothScrollTo(0, 0)
                    }
                    InteractiveCoachManager.TrainingStep.STEP_6_CONFIG_PROXIMITY -> {
                        setSecretLockState(binding.swTrigProximity)
                        binding.cardFlip.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start()
                        binding.swTrigProximity.animate().scaleX(1.08f).scaleY(1.08f).setDuration(300).start()
                        showSecretPointerAbove(binding.swTrigProximity, "👇 Turn ON Proximity Wave")
                        binding.tabMagic.post {
                            binding.tabMagic.smoothScrollTo(0, binding.swTrigProximity.top)
                        }
                    }
                    InteractiveCoachManager.TrainingStep.STEP_6B_OPEN_EDIT_TAB -> {
                        binding.swTrigProximity.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start()
                        setSecretLockState(binding.btnEditParams)
                        binding.btnEditParams.animate().scaleX(1.25f).scaleY(1.25f).setDuration(300).start()
                        showSecretPointerBelow(binding.btnEditParams, "👇 Tap Edit")
                    }
                    InteractiveCoachManager.TrainingStep.STEP_6C_SAVE_FLIP_PARAMS -> {
                        binding.btnEditParams.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start()
                        binding.etFlipTrigger.setText("66")
                        binding.etFlipResult.setText("99")
                        setSecretLockState(binding.btnSaveFlip)
                        binding.btnSaveFlip.animate().scaleX(1.06f).scaleY(1.06f).setDuration(300).start()
                        showSecretPointerAbove(binding.btnSaveFlip, "👇 Tap Save Flip (66 -> 99)")
                        binding.tabEdit.post {
                            binding.tabEdit.smoothScrollTo(0, binding.btnSaveFlip.top)
                        }
                    }
                    InteractiveCoachManager.TrainingStep.STEP_7_TAP_BACK -> {
                        binding.btnSaveFlip.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start()
                        val navBackView = binding.bottomNav.findViewById<View>(R.id.nav_back)
                        if (navBackView != null) {
                            setSecretLockState(navBackView)
                            navBackView.animate()?.scaleX(1.2f)?.scaleY(1.2f)?.setDuration(400)?.start()
                            showSecretPointerAbove(navBackView, "👇 Tap Calc to Perform!")
                        }
                    }
                    else -> {
                        setSecretLockState(null)
                        hideSecretPointer()
                    }
                }
            }
        }
    }

    private fun setSecretLockState(activeTarget: View?) {
        val magicCards = listOf(
            binding.cardFlip, binding.cardLucky, binding.cardPhone,
            findViewById<View>(R.id.cardContactForceSetting),
            findViewById<View>(R.id.cardP2PLinkSetting),
            findViewById<View>(R.id.cardToxicGallerySetting),
            findViewById<View>(R.id.cardStepRevealSetting),
            findViewById<View>(R.id.cardMoiLockSetting),
            findViewById<View>(R.id.cardExplosionSetting),
            findViewById<View>(R.id.cardToxicSetting),
            findViewById<View>(R.id.cardGhostSetting),
            findViewById<View>(R.id.cardRecoverySetting),
            findViewById<View>(R.id.cardWallpaperMagicSetting)
        ).filterNotNull()

        val sensorSwitches = listOf(
            binding.swTrigProximity, binding.swTrigLight, binding.swTrigMagnetic,
            binding.swTrigShake, binding.swTrigTilt, binding.swTrigFaceDown,
            binding.swTrigGyro, binding.swTrigVolUp, binding.swTrigVolDown,
            binding.swTrigClap, binding.swTrigFingerprint, binding.swTrigRotate5
        )

        if (activeTarget == null) {
            magicCards.forEach { card ->
                card.isEnabled = true
                card.alpha = 1.0f
            }
            binding.swFlip.isEnabled = true
            binding.swLucky.isEnabled = true
            binding.swPhone.isEnabled = true
            sensorSwitches.forEach { sw ->
                sw.isEnabled = true
                sw.alpha = 1.0f
            }
            binding.btnEditParams.isEnabled = true
            binding.btnEditParams.alpha = 1.0f
            binding.btnSaveFlip.isEnabled = true
            binding.btnSaveFlip.alpha = 1.0f
            for (i in 0 until binding.bottomNav.menu.size()) {
                binding.bottomNav.menu.getItem(i).isEnabled = true
            }
            binding.bottomNav.alpha = 1.0f
        } else {
            magicCards.forEach { card ->
                val isTarget = (card == activeTarget || card.findViewById<View>(activeTarget.id) != null)
                card.isEnabled = isTarget
                card.alpha = if (isTarget) 1.0f else 0.28f
            }
            binding.swFlip.isEnabled = (activeTarget == binding.swFlip || activeTarget == binding.cardFlip)
            binding.swLucky.isEnabled = false
            binding.swPhone.isEnabled = false

            sensorSwitches.forEach { sw ->
                val isTarget = (sw == activeTarget)
                sw.isEnabled = isTarget
                sw.alpha = if (isTarget) 1.0f else 0.28f
            }

            binding.btnEditParams.isEnabled = (activeTarget == binding.btnEditParams)
            binding.btnEditParams.alpha = if (activeTarget == binding.btnEditParams) 1.0f else 0.28f

            binding.btnSaveFlip.isEnabled = (activeTarget == binding.btnSaveFlip)
            binding.btnSaveFlip.alpha = if (activeTarget == binding.btnSaveFlip) 1.0f else 0.28f

            val isNavBackTarget = (activeTarget.id == R.id.nav_back)
            for (i in 0 until binding.bottomNav.menu.size()) {
                val item = binding.bottomNav.menu.getItem(i)
                item.isEnabled = isNavBackTarget && (item.itemId == R.id.nav_back)
            }
            binding.bottomNav.alpha = if (isNavBackTarget) 1.0f else 0.35f
        }
    }

    private fun showSecretPointerAbove(targetView: View, text: String) {
        val pointerLayout = binding.layoutSecretCoachPointer
        val tvBubble = binding.tvSecretPointerBubble
        val imgArrow = binding.imgSecretPointerArrow

        tvBubble.text = text
        imgArrow.setImageResource(R.drawable.ic_arrow_downward)
        pointerLayout.visibility = View.VISIBLE

        targetView.post {
            val locTarget = IntArray(2)
            val locMain = IntArray(2)
            targetView.getLocationOnScreen(locTarget)
            binding.root.getLocationOnScreen(locMain)

            pointerLayout.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
            val pointerW = pointerLayout.measuredWidth
            val pointerH = pointerLayout.measuredHeight

            val targetCenterX = (locTarget[0] - locMain[0]) + (targetView.width / 2f)
            val targetTopY = (locTarget[1] - locMain[1]).toFloat()

            pointerLayout.x = (targetCenterX - (pointerW / 2f)).coerceIn(16f, (binding.root.width - pointerW - 16).toFloat())
            pointerLayout.y = targetTopY - pointerH - 8f

            pointerLayout.clearAnimation()
            val bobAnimation = android.view.animation.TranslateAnimation(0f, 0f, 0f, -20f).apply {
                duration = 450
                repeatMode = android.view.animation.Animation.REVERSE
                repeatCount = android.view.animation.Animation.INFINITE
                interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            }
            pointerLayout.startAnimation(bobAnimation)
        }
    }

    private fun showSecretPointerBelow(targetView: View, text: String) {
        val pointerLayout = binding.layoutSecretCoachPointer
        val tvBubble = binding.tvSecretPointerBubble
        val imgArrow = binding.imgSecretPointerArrow

        tvBubble.text = text
        imgArrow.setImageResource(R.drawable.ic_arrow_upward)
        pointerLayout.visibility = View.VISIBLE

        targetView.post {
            val locTarget = IntArray(2)
            val locMain = IntArray(2)
            targetView.getLocationOnScreen(locTarget)
            binding.root.getLocationOnScreen(locMain)

            pointerLayout.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
            val pointerW = pointerLayout.measuredWidth

            val targetCenterX = (locTarget[0] - locMain[0]) + (targetView.width / 2f)
            val targetBottomY = (locTarget[1] - locMain[1]) + targetView.height.toFloat()

            pointerLayout.x = (targetCenterX - (pointerW / 2f)).coerceIn(16f, (binding.root.width - pointerW - 16).toFloat())
            pointerLayout.y = targetBottomY + 8f

            pointerLayout.clearAnimation()
            val bobAnimation = android.view.animation.TranslateAnimation(0f, 0f, 0f, 20f).apply {
                duration = 450
                repeatMode = android.view.animation.Animation.REVERSE
                repeatCount = android.view.animation.Animation.INFINITE
                interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            }
            pointerLayout.startAnimation(bobAnimation)
        }
    }

    private fun hideSecretPointer() {
        binding.layoutSecretCoachPointer.clearAnimation()
        binding.layoutSecretCoachPointer.visibility = View.GONE
    }

    private fun initUI() {
        binding.sbSensitivity.progress = settingsManager.shakeSensitivity.toInt()
        binding.sbVolume.progress = settingsManager.soundVolume
        binding.sbHaptic.progress = settingsManager.hapticIntensity

        binding.etForcedPhone.setText(settingsManager.forcedPhoneNumber)
        binding.etFlipTrigger.setText(settingsManager.customFlipTrigger)
        binding.etFlipResult.setText(settingsManager.customFlipResult)

        when (settingsManager.appMode) {
            0 -> binding.rbMagicCalc.isChecked = true
            1 -> binding.rbDialer.isChecked = true
            2 -> {
                settingsManager.appMode = 0
                binding.rbMagicCalc.isChecked = true
            }
            3 -> binding.rbGallery.isChecked = true
            4 -> binding.rbMagicCalc.isChecked = true
        }

        setupAppModeListener()

        val pinManager = PinManager(this)
        val wallpaperPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                try {
                    val inputStream = contentResolver.openInputStream(uri)
                    val outputFile = File(filesDir, "moilock_wallpaper.jpg")
                    val outputStream = FileOutputStream(outputFile)
                    inputStream?.copyTo(outputStream)
                    inputStream?.close()
                    outputStream.close()
                    pinManager.customWallpaperPath = outputFile.absolutePath
                    MagicToast.show(this, "Custom Wallpaper Saved!")
                } catch (e: Exception) {
                    e.printStackTrace()
                    MagicToast.show(this, "Failed to save wallpaper")
                }
            }
        }

        findViewById<View>(R.id.btnProfile)?.setOnClickListener {
            SecretDialogHelper.showProfilePanelDialog(
                activity = this,
                onPermissionsCheck = { checkAndRequestMoiLockPermissions() },
                onSubscriptionRestored = { applySubscriptionState() }
            )
        }

        updateMoiLockIconStatus()
        updateToxicGalleryIconStatus()
        updateP2PIconStatus()
        updateStepRevealIconStatus()
        updateExplosionIconStatus()
        updateContactForceIconStatus()
        updateToxicTimeIconStatus()
        updateGhostTypingIconStatus()
        updateGhostRecoveryIconStatus()

        findViewById<View>(R.id.cardP2PLinkSetting)?.setOnClickListener {
            checkPremiumAndRun {
                SecretDialogHelper.showP2PLinkPanelDialog(
                    activity = this,
                    settingsManager = settingsManager,
                    onActivateMagic = { key -> activateMagic(key) },
                    onStatusChanged = { updateP2PIconStatus() }
                )
            }
        }

        findViewById<View>(R.id.cardToxicGallerySetting)?.setOnClickListener {
            checkPremiumAndRun {
                SecretDialogHelper.showToxicGalleryPanelDialog(this, settingsManager) { key ->
                    activateMagic(key)
                }
            }
        }

        findViewById<View>(R.id.cardStepRevealSetting)?.setOnClickListener {
            checkPremiumAndRun {
                SecretDialogHelper.showStepRevealPanelDialog(
                    activity = this,
                    settingsManager = settingsManager,
                    onActivateMagic = { key -> activateMagic(key) },
                    onStatusChanged = { updateStepRevealIconStatus() }
                )
            }
        }

        findViewById<View>(R.id.cardMoiLockSetting)?.setOnClickListener {
            checkPremiumAndRun {
                SecretDialogHelper.showMoiLockPanelDialog(
                    activity = this,
                    settingsManager = settingsManager,
                    wallpaperPickerLauncher = wallpaperPickerLauncher,
                    onActivateMagic = { key -> activateMagic(key) },
                    onStatusChanged = { updateMoiLockIconStatus() }
                )
            }
        }

        findViewById<View>(R.id.cardExplosionSetting)?.setOnClickListener {
            checkPremiumAndRun {
                SecretDialogHelper.showExplosionPanelDialog(
                    activity = this,
                    settingsManager = settingsManager,
                    onActivateMagic = { key -> activateMagic(key) },
                    onStatusChanged = { updateExplosionIconStatus() }
                )
            }
        }

        findViewById<View>(R.id.cardContactForceSetting)?.setOnClickListener {
            checkPremiumAndRun {
                SecretDialogHelper.showContactForceConfigDialog(
                    activity = this,
                    settingsManager = settingsManager,
                    onActivateMagic = { key -> activateMagic(key) },
                    onStatusChanged = { updateContactForceIconStatus() }
                )
            }
        }

        findViewById<View>(R.id.cardToxicSetting)?.setOnClickListener {
            checkPremiumAndRun {
                SecretDialogHelper.showToxicTimeConfigDialog(
                    activity = this,
                    settingsManager = settingsManager,
                    onActivateMagic = { key -> activateMagic(key) },
                    onStatusChanged = { updateToxicTimeIconStatus() }
                )
            }
        }

        findViewById<View>(R.id.cardGhostSetting)?.setOnClickListener {
            checkPremiumAndRun {
                SecretDialogHelper.showGhostTypingConfigDialog(
                    activity = this,
                    settingsManager = settingsManager,
                    onActivateMagic = { key -> activateMagic(key) },
                    onStatusChanged = { updateGhostTypingIconStatus() }
                )
            }
        }

        findViewById<View>(R.id.cardRecoverySetting)?.setOnClickListener {
            checkPremiumAndRun {
                SecretDialogHelper.showGhostRecoveryConfigDialog(
                    activity = this,
                    settingsManager = settingsManager,
                    onActivateMagic = { key -> activateMagic(key) },
                    onStatusChanged = { updateGhostRecoveryIconStatus() }
                )
            }
        }

        findViewById<View>(R.id.cardWallpaperMagicSetting)?.setOnClickListener {
            checkPremiumAndRun {
                SecretDialogHelper.showWallpaperMagicConfigDialog(
                    activity = this,
                    settingsManager = settingsManager,
                    onActivateMagic = { key -> activateMagic(key) },
                    onStatusChanged = { updateWallpaperMagicIconStatus() }
                )
            }
        }

        findViewById<View>(R.id.cardSubmitIdeaSetting)?.setOnClickListener {
            SecretDialogHelper.showSubmitIdeaDialog(this)
        }

        findViewById<View>(R.id.btnPhoneInfo)?.setOnClickListener {
            SecretDialogHelper.showContactForceGuideDialog(this, settingsManager) { key ->
                activateMagic(key)
            }
        }

        applyActiveMagicUI(settingsManager.activeMagic)

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

        binding.btnSettings.setOnClickListener { binding.tabSettingsOverlay.visibility = View.VISIBLE }
        binding.btnCloseSettings.setOnClickListener {
            saveSystemSettings()
            binding.tabSettingsOverlay.visibility = View.GONE
        }
        setupSimpleMagicCardClicks()
        setupSensorHelpListeners()
        updateAdvancedCardPremiumBadges()
        setupAppIconTab()
        setupAppModeListener()
    }

    private fun setupAppModeListener() {
        binding.rgAppMode.setOnCheckedChangeListener(null)
        when (settingsManager.appMode) {
            0 -> binding.rbMagicCalc.isChecked = true
            1 -> if (PremiumManager.isUserPremium) binding.rbDialer.isChecked = true else binding.rbMagicCalc.isChecked = true
            3 -> if (PremiumManager.isUserPremium) binding.rbGallery.isChecked = true else binding.rbMagicCalc.isChecked = true
            else -> binding.rbMagicCalc.isChecked = true
        }

        binding.rgAppMode.setOnCheckedChangeListener { _, checkedId ->
            if ((checkedId == binding.rbDialer.id || checkedId == binding.rbGallery.id) && !PremiumManager.isUserPremium) {
                binding.rgAppMode.setOnCheckedChangeListener(null)
                when (settingsManager.appMode) {
                    0 -> binding.rbMagicCalc.isChecked = true
                    1 -> if (PremiumManager.isUserPremium) binding.rbDialer.isChecked = true else binding.rbMagicCalc.isChecked = true
                    3 -> if (PremiumManager.isUserPremium) binding.rbGallery.isChecked = true else binding.rbMagicCalc.isChecked = true
                    else -> binding.rbMagicCalc.isChecked = true
                }
                setupAppModeListener()
                startActivity(Intent(this, PremiumActivity::class.java))
                val modeName = if (checkedId == binding.rbDialer.id) "Phone Dialer mode" else "Gallery mode"
                MagicToast.show(this, "Premium required for $modeName!")
                return@setOnCheckedChangeListener
            }

            when (checkedId) {
                binding.rbMagicCalc.id -> settingsManager.appMode = 0
                binding.rbDialer.id -> settingsManager.appMode = 1
                binding.rbGallery.id -> settingsManager.appMode = 3
            }
            updateMoiLockIconStatus()

            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            finish()
        }
    }

    private fun magicNeedsSensor(magicKey: String): Boolean {
        return magicKey in listOf("DIGIT_FLIP", "GHOST_TYPING", "REAPPEARING_RESULT")
    }

    private fun activateMagic(key: String) {
        settingsManager.activeMagic = key
        settingsManager.isContactForceEnabled = (key == "CONTACT_FORCE")
        settingsManager.isExplosionEnabled = (key == "BUTTON_EXPLOSION")

        // Handle MoiLock Mutual Exclusivity
        if (key == "MOILOCK") {
            settingsManager.appMode = 4
        } else {
            if (settingsManager.appMode == 4) {
                settingsManager.appMode = 0
                val serviceIntent = Intent(this, MoiLockService::class.java)
                try { stopService(serviceIntent) } catch (e: Exception) { e.printStackTrace() }
            }
        }

        // Handle P2P Mutual Exclusivity
        if (key != "P2P_LINK") {
            settingsManager.isP2PEnabled = false
        }

        // Handle Wallpaper & Voice Mutual Exclusivity
        if (key != "WALLPAPER_MAGIC") {
            settingsManager.isWallpaperMagicEnabled = false
            settingsManager.isVoiceListenerEnabled = false
        }

        val allSwitches = listOf(
            binding.swFlip to "DIGIT_FLIP",
            binding.swLucky to "LUCKY",
            binding.swPhone to "PHONE_NUMBER_FORCE"
        )
        for ((sw, magicId) in allSwitches) {
            if (sw.isChecked != (magicId == key)) {
                sw.setOnCheckedChangeListener(null)
                sw.isChecked = (magicId == key)
            }
        }
        setupMagicSwitches()

        val sensorSection = findViewById<View>(R.id.layoutSensorTriggerSection)
        if (magicNeedsSensor(key)) {
            sensorSection?.visibility = View.VISIBLE
            sensorSection?.alpha = 1.0f
            if (settingsManager.activeSensorTrigger == "NONE") {
                settingsManager.activeSensorTrigger = "PROXIMITY"
                binding.swTrigProximity.isChecked = true
            }
        } else {
            sensorSection?.visibility = View.GONE
            if (key != "NONE") {
                settingsManager.activeSensorTrigger = "NONE"
                clearAllTriggerSwitchesSilently()
            }
        }

        updateToxicGalleryIconStatus()
        updateMoiLockIconStatus()
        updateP2PIconStatus()
        updateStepRevealIconStatus()
        updateExplosionIconStatus()
        updateContactForceIconStatus()
        updateToxicTimeIconStatus()
        updateGhostTypingIconStatus()
        updateGhostRecoveryIconStatus()
        updateWallpaperMagicIconStatus()
        updateSimpleMagicUIColors()
    }

    private fun applyActiveMagicUI(key: String) {
        val allSwitches = listOf(
            binding.swFlip to "DIGIT_FLIP",
            binding.swLucky to "LUCKY",
            binding.swPhone to "PHONE_NUMBER_FORCE"
        )
        for ((sw, magicId) in allSwitches) {
            sw.setOnCheckedChangeListener(null)
            sw.isChecked = (magicId == key)
        }
        setupMagicSwitches()

        val sensorSection = findViewById<View>(R.id.layoutSensorTriggerSection)
        if (magicNeedsSensor(key)) {
            sensorSection?.visibility = View.VISIBLE
            sensorSection?.alpha = 1.0f
        } else {
            sensorSection?.visibility = View.GONE
        }
        updateSimpleMagicUIColors()
        updateWallpaperMagicIconStatus()
    }

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

    private fun checkPremiumAndRun(action: () -> Unit) {
        if (PremiumManager.isUserPremium) {
            action()
        } else {
            startActivity(Intent(this, PremiumActivity::class.java))
        }
    }

    private fun setupMagicSwitches() {
        binding.swFlip.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                activateMagic("DIGIT_FLIP")
                InteractiveCoachManager.notifyFlipSwitched(true)
            } else if (settingsManager.activeMagic == "DIGIT_FLIP") {
                activateMagic("NONE")
            }
        }
        binding.swLucky.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) activateMagic("LUCKY")
            else if (settingsManager.activeMagic == "LUCKY") activateMagic("NONE")
        }
        binding.swPhone.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) activateMagic("PHONE_NUMBER_FORCE")
            else if (settingsManager.activeMagic == "PHONE_NUMBER_FORCE") activateMagic("NONE")
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
                    if (type == "PROXIMITY") {
                        InteractiveCoachManager.notifyProximitySwitched(true)
                    }
                } else if (settingsManager.activeSensorTrigger == type) {
                    updateSensorTrigger("NONE")
                }
            }
        }
    }

    private fun updateSensorTrigger(type: String) {
        settingsManager.activeSensorTrigger = type
        if (type != "NONE") {
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
            SecretDialogHelper.showPerformanceGuideDialog(
                this,
                "Digital Flip",
                listOf(
                    "1. Configure your Trigger (e.g. 66) and Result (e.g. 99) under the 'Edit Parameters' tab.",
                    "2. During performance, enter your Trigger number (66) on the main calculator dialer.",
                    "3. Press the '=' key to show it normally.",
                    "4. Perform your selected sensor trigger secretly (e.g., Wave hand over screen or Tilt phone).",
                    "5. The display will magically flip and instantly update into your forced Result (99)!"
                ),
                "#00E5FF",
                onPracticeClick = {
                    settingsManager.activeMagic = "DIGIT_FLIP"
                    settingsManager.activeSensorTrigger = "PROXIMITY"
                    saveSystemSettings()
                    InteractiveCoachManager.startDigitalFlipPractice()
                    val intent = Intent(this, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                    startActivity(intent)
                    finish()
                }
            )
        }
        binding.cardLucky.setOnClickListener {
            SecretDialogHelper.showPerformanceGuideDialog(
                this,
                "Lucky Number Force",
                listOf(
                    "1. Type '777' on the main calculator dialer and press '=' to arm Lucky Force mode.",
                    "2. Quickly force any number 1-6 using secret shortcuts:\n   - 1: Proximity Wave\n   - 2: Face Down\n   - 3: Volume UP\n   - 4: Volume DOWN\n   - 5: Light Shadow\n   - 6: Shake Phone",
                    "3. The spectator types any normal calculation (e.g., 5 + 5).",
                    "4. Pressing '=' on any equation will force the Lucky Number to be computed instead of the actual math!"
                ),
                "#FF2D55"
            )
        }
        binding.cardPhone.setOnClickListener {
            SecretDialogHelper.showPerformanceGuideDialog(
                this,
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
            Triple("PHONE_NUMBER_FORCE", binding.imgPhoneIconLeft, binding.txtPhoneTitleLeft)
        )

        val activeColors = mapOf(
            "DIGIT_FLIP" to "#00E5FF",
            "LUCKY" to "#FF2D55",
            "PHONE_NUMBER_FORCE" to "#00FF66"
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

    private fun showHelp(msg: String) {
        MagicToast.show(this, msg, isLong = true)
    }

    private fun setupSaveButtons() {
        binding.btnSavePhone.setOnClickListener {
            settingsManager.forcedPhoneNumber = binding.etForcedPhone.text.toString()
            MagicToast.show(this, "Phone Magic Saved!")
        }
        binding.btnSaveFlip.setOnClickListener {
            val trigger = binding.etFlipTrigger.text.toString().ifEmpty { "66" }
            val result = binding.etFlipResult.text.toString().ifEmpty { "99" }
            settingsManager.customFlipTrigger = trigger
            settingsManager.customFlipResult = result
            MagicToast.show(this, "Digital Flip Saved!")
            InteractiveCoachManager.notifyFlipSaved()
        }
    }

    private fun setupNavigation() {
        binding.btnEditParams.setOnClickListener {
            hideAllTabs()
            binding.tabEdit.visibility = View.VISIBLE
            binding.tvTabTitle.text = "Edit Parameters"
            InteractiveCoachManager.notifyEditTabOpened()
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            if (InteractiveCoachManager.isTrainingActive) {
                val current = InteractiveCoachManager.currentStep.value
                if (current != InteractiveCoachManager.TrainingStep.STEP_7_TAP_BACK || item.itemId != R.id.nav_back) {
                    return@setOnItemSelectedListener false
                }
            }
            hideAllTabs()
            when (item.itemId) {
                R.id.nav_magic -> {
                    binding.tabMagic.visibility = View.VISIBLE
                    binding.tvTabTitle.text = "Magic List"
                }
                R.id.nav_icons -> {
                    binding.tabIcons.visibility = View.VISIBLE
                    binding.tvTabTitle.text = "App Icons"
                    updateAppIconSelectionUI()
                }
                R.id.nav_theme -> {
                    binding.tabTheme.visibility = View.VISIBLE
                    binding.tvTabTitle.text = "Theme"
                }
                R.id.nav_back -> {
                    InteractiveCoachManager.notifyBackToCalc()
                    saveSystemSettings()
                    val intent = Intent(this, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                    startActivity(intent)
                    finish()
                }
            }
            true
        }
    }

    private fun setupAppIconTab() {
        binding.cardIconCalculator.setOnClickListener { selectAppIcon(AppIconManager.AppIconType.CALCULATOR) }
        binding.cardIconCalendar.setOnClickListener { selectAppIcon(AppIconManager.AppIconType.CALENDAR) }
        binding.cardIconNotes.setOnClickListener { selectAppIcon(AppIconManager.AppIconType.NOTES) }
        binding.cardIconSettings.setOnClickListener { selectAppIcon(AppIconManager.AppIconType.SETTINGS) }
        binding.cardIconMessages.setOnClickListener { selectAppIcon(AppIconManager.AppIconType.MESSAGES) }
        updateAppIconSelectionUI()
    }

    private fun selectAppIcon(iconType: AppIconManager.AppIconType) {
        if (iconType != AppIconManager.AppIconType.CALCULATOR && !PremiumManager.isUserPremium) {
            startActivity(Intent(this, PremiumActivity::class.java))
            MagicToast.show(this, "Premium required to unlock ${iconType.title} disguise icon!")
            return
        }
        val success = AppIconManager.setAppIcon(this, iconType)
        if (success) {
            MagicToast.show(this, "Launcher icon updated to ${iconType.title}!")
            updateAppIconSelectionUI()
        } else {
            MagicToast.show(this, "Failed to update app icon.")
        }
    }

    private fun updateAppIconSelectionUI() {
        val current = AppIconManager.getActiveAppIcon(this)

        val activeCardBg = Color.parseColor("#252538")
        val inactiveCardBg = Color.parseColor("#1A1A24")
        val activeCheckColor = Color.parseColor("#00E5FF")
        val inactiveCheckColor = Color.parseColor("#55FFFFFF")

        val allCards = listOf(
            binding.cardIconCalculator to AppIconManager.AppIconType.CALCULATOR,
            binding.cardIconCalendar to AppIconManager.AppIconType.CALENDAR,
            binding.cardIconNotes to AppIconManager.AppIconType.NOTES,
            binding.cardIconSettings to AppIconManager.AppIconType.SETTINGS,
            binding.cardIconMessages to AppIconManager.AppIconType.MESSAGES
        )

        val allChecks = listOf(
            binding.imgIconCalculatorCheck to AppIconManager.AppIconType.CALCULATOR,
            binding.imgIconCalendarCheck to AppIconManager.AppIconType.CALENDAR,
            binding.imgIconNotesCheck to AppIconManager.AppIconType.NOTES,
            binding.imgIconSettingsCheck to AppIconManager.AppIconType.SETTINGS,
            binding.imgIconMessagesCheck to AppIconManager.AppIconType.MESSAGES
        )

        for ((card, type) in allCards) {
            card.setCardBackgroundColor(if (type == current) activeCardBg else inactiveCardBg)
        }

        for ((check, type) in allChecks) {
            if (type == current) {
                check.setImageResource(R.drawable.ic_dot_filled)
                check.setColorFilter(activeCheckColor)
            } else {
                check.setImageResource(R.drawable.ic_dot_empty)
                check.setColorFilter(inactiveCheckColor)
            }
        }
    }

    private fun hideAllTabs() {
        binding.tabMagic.visibility = View.GONE
        binding.tabIcons.visibility = View.GONE
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

        if (BuildConfig.DEBUG) {
            addDebugCrashTestButton()
        }
    }

    private fun addDebugCrashTestButton() {
        val button = com.google.android.material.button.MaterialButton(this).apply {
            text = "🧪 Trigger Test Crash (Debug Only)"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#B00020"))
            setOnClickListener {
                throw RuntimeException("Test Crash from SecretActivity Sensor Lab (Crashlytics pipeline test)")
            }
        }
        val lp = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, 16, 0, 16)
        }
        binding.sensorListContainer.addView(button, lp)
    }

    private fun addSensorView(id: String, name: String, available: Boolean) {
        val card = LayoutInflater.from(this).inflate(R.layout.item_sensor_test, binding.sensorListContainer, false)
        val tvName = card.findViewById<TextView>(R.id.tvSensorName)
        val tvStatus = card.findViewById<TextView>(R.id.tvSensorStatus)
        val tvValue = card.findViewById<TextView>(R.id.tvSensorValue)

        tvName.text = name
        if (available) {
            tvStatus.text = "ONLINE"
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.dialer_green))
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

                        withContext(Dispatchers.Main) {
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
                    delay(100)
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
            val allGranted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                val name = when (requestCode) {
                    201 -> "Contacts"
                    203 -> "Call Log"
                    else -> "Microphone"
                }
                MagicToast.show(this, "✓ $name permission granted!")
            } else {
                MagicToast.show(this, "Permission denied. Tap Enable again in Settings.", isLong = true)
            }
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

    private fun checkAndRequestMoiLockPermissions() {
        var needsPermission = false
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
        val componentName = android.content.ComponentName(this, MoiDeviceAdminReceiver::class.java)
        if (!dpm.isAdminActive(componentName)) {
            val intent = Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
                putExtra(android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Required for MoiLock to simulate realistic lock screen.")
            }
            startActivity(intent)
            needsPermission = true
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(this)) {
            val intent = Intent(
                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:$packageName")
            )
            startActivity(intent)
            needsPermission = true
        }

        if (needsPermission) {
            MagicToast.show(this, "Please grant the requested permission for MoiLock.", isLong = true)
        } else {
            MagicToast.show(this, "All required MoiLock permissions are already granted!")
        }
    }

    private fun updateMoiLockIconStatus() {
        val imgMoiLockIconLeft = findViewById<android.widget.ImageView>(R.id.imgMoiLockIconLeft) ?: return
        val txtMoiLockTitleLeft = findViewById<TextView>(R.id.txtMoiLockTitleLeft) ?: return
        if (settingsManager.appMode == 4 && settingsManager.activeMagic == "MOILOCK") {
            imgMoiLockIconLeft.setColorFilter(Color.parseColor("#A855F7"))
            txtMoiLockTitleLeft.setTextColor(Color.parseColor("#A855F7"))
        } else {
            imgMoiLockIconLeft.setColorFilter(Color.parseColor("#88FFFFFF"))
            txtMoiLockTitleLeft.setTextColor(Color.parseColor("#88FFFFFF"))
        }
    }

    private fun updateToxicGalleryIconStatus() {
        val imgGalleryIconLeft = findViewById<android.widget.ImageView>(R.id.imgGalleryIconLeft) ?: return
        val txtGalleryTitleLeft = findViewById<TextView>(R.id.txtGalleryTitleLeft) ?: return
        if (settingsManager.activeMagic == "TOXIC_GALLERY") {
            imgGalleryIconLeft.setColorFilter(Color.parseColor("#FF2D55"))
            txtGalleryTitleLeft.setTextColor(Color.parseColor("#FF2D55"))
        } else {
            imgGalleryIconLeft.setColorFilter(Color.parseColor("#88FFFFFF"))
            txtGalleryTitleLeft.setTextColor(Color.parseColor("#88FFFFFF"))
        }
    }

    private fun updateExplosionIconStatus() {
        val imgExplosionIconLeft = findViewById<android.widget.ImageView>(R.id.imgExplosionIconLeft) ?: return
        val txtExplosionTitleLeft = findViewById<TextView>(R.id.txtExplosionTitleLeft) ?: return
        if (settingsManager.isExplosionEnabled && settingsManager.activeMagic == "BUTTON_EXPLOSION") {
            imgExplosionIconLeft.setColorFilter(Color.parseColor("#FF9100"))
            txtExplosionTitleLeft.setTextColor(Color.parseColor("#FF9100"))
        } else {
            imgExplosionIconLeft.setColorFilter(Color.parseColor("#88FFFFFF"))
            txtExplosionTitleLeft.setTextColor(Color.parseColor("#88FFFFFF"))
        }
    }

    private fun updateP2PIconStatus() {
        val imgIcon = findViewById<android.widget.ImageView>(R.id.imgP2PLinkIconLeft) ?: return
        val txtTitle = findViewById<TextView>(R.id.txtP2PLinkTitleLeft) ?: return
        val p2p = P2PLinkManager.getInstance()
        if (p2p.isConnected || (settingsManager.isP2PEnabled && settingsManager.activeMagic == "P2P_LINK")) {
            imgIcon.setColorFilter(Color.parseColor("#00E5FF"))
            txtTitle.setTextColor(Color.parseColor("#00E5FF"))
        } else {
            imgIcon.setColorFilter(Color.parseColor("#88FFFFFF"))
            txtTitle.setTextColor(Color.parseColor("#88FFFFFF"))
        }
    }

    private fun updateStepRevealIconStatus() {
        val imgStepRevealSettingIconLeft = findViewById<android.widget.ImageView>(R.id.imgStepRevealSettingIconLeft) ?: return
        val txtStepRevealSettingTitleLeft = findViewById<TextView>(R.id.txtStepRevealSettingTitleLeft) ?: return
        if (settingsManager.activeMagic == "STEP_REVEAL") {
            imgStepRevealSettingIconLeft.setColorFilter(Color.parseColor("#00E5FF"))
            txtStepRevealSettingTitleLeft.setTextColor(Color.parseColor("#00E5FF"))
        } else {
            imgStepRevealSettingIconLeft.setColorFilter(Color.parseColor("#88FFFFFF"))
            txtStepRevealSettingTitleLeft.setTextColor(Color.parseColor("#88FFFFFF"))
        }
    }

    private fun updateContactForceIconStatus() {
        val enabled = settingsManager.isContactForceEnabled
        val color = if (enabled) Color.parseColor("#00E5FF") else Color.parseColor("#88FFFFFF")
        findViewById<android.widget.ImageView>(R.id.imgContactForceIcon)?.setColorFilter(color)
        findViewById<TextView>(R.id.txtContactForceTitle)?.setTextColor(color)
    }

    private fun updateToxicTimeIconStatus() {
        val imgIcon = findViewById<android.widget.ImageView>(R.id.imgToxicSettingIconLeft) ?: return
        val txtTitle = findViewById<TextView>(R.id.txtToxicSettingTitleLeft) ?: return
        if (settingsManager.activeMagic == "TOXIC_TIME") {
            imgIcon.setColorFilter(Color.parseColor("#A855F7"))
            txtTitle.setTextColor(Color.parseColor("#A855F7"))
        } else {
            imgIcon.setColorFilter(Color.parseColor("#88FFFFFF"))
            txtTitle.setTextColor(Color.parseColor("#88FFFFFF"))
        }
    }

    private fun updateGhostTypingIconStatus() {
        val imgIcon = findViewById<android.widget.ImageView>(R.id.imgGhostSettingIconLeft) ?: return
        val txtTitle = findViewById<TextView>(R.id.txtGhostSettingTitleLeft) ?: return
        if (settingsManager.activeMagic == "GHOST_TYPING") {
            imgIcon.setColorFilter(Color.parseColor("#FFCC00"))
            txtTitle.setTextColor(Color.parseColor("#FFCC00"))
        } else {
            imgIcon.setColorFilter(Color.parseColor("#88FFFFFF"))
            txtTitle.setTextColor(Color.parseColor("#88FFFFFF"))
        }
    }

    private fun updateGhostRecoveryIconStatus() {
        val imgIcon = findViewById<android.widget.ImageView>(R.id.imgRecoverySettingIconLeft) ?: return
        val txtTitle = findViewById<TextView>(R.id.txtRecoverySettingTitleLeft) ?: return
        if (settingsManager.activeMagic == "REAPPEARING_RESULT") {
            imgIcon.setColorFilter(Color.parseColor("#FF9100"))
            txtTitle.setTextColor(Color.parseColor("#FF9100"))
        } else {
            imgIcon.setColorFilter(Color.parseColor("#88FFFFFF"))
            txtTitle.setTextColor(Color.parseColor("#88FFFFFF"))
        }
    }

    private fun checkPermissionsWarningState() {
        val cardWarning = findViewById<androidx.cardview.widget.CardView>(R.id.cardPermissionWarning) ?: return

        fun requestRuntime(perm: String, code: Int) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, perm) ||
                ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(perm), code)
            } else {
                try {
                    startActivity(Intent(
                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.parse("package:$packageName")
                    ))
                    MagicToast.show(this, "Tap Permissions and enable manually.", isLong = true)
                } catch (e: Exception) {
                    ActivityCompat.requestPermissions(this, arrayOf(perm), code)
                }
            }
        }

        val rowOverlay = findViewById<View>(R.id.rowPermOverlay)
        val btnOverlay = findViewById<android.widget.Button>(R.id.btnGrantOverlay)
        val missingOverlay = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M &&
                !android.provider.Settings.canDrawOverlays(this)
        rowOverlay?.visibility = if (missingOverlay) View.VISIBLE else View.GONE
        btnOverlay?.setOnClickListener {
            try {
                startActivity(Intent(
                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:$packageName")
                ))
                MagicToast.show(this, "Enable 'Display over other apps' then come back.", isLong = true)
            } catch (e: Exception) {
                startActivity(Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:$packageName")
                ))
            }
        }

        val rowContacts = findViewById<View>(R.id.rowPermContacts)
        val btnContacts = findViewById<android.widget.Button>(R.id.btnGrantContacts)
        val missingContacts = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED
        rowContacts?.visibility = if (missingContacts) View.VISIBLE else View.GONE
        btnContacts?.setOnClickListener { requestRuntime(Manifest.permission.READ_CONTACTS, 201) }

        val rowCallLog = findViewById<View>(R.id.rowPermCallLog)
        val btnCallLog = findViewById<android.widget.Button>(R.id.btnGrantCallLog)
        val missingCallLog = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED
        rowCallLog?.visibility = if (missingCallLog) View.VISIBLE else View.GONE
        btnCallLog?.setOnClickListener { requestRuntime(Manifest.permission.READ_CALL_LOG, 203) }

        val rowMic = findViewById<View>(R.id.rowPermMic)
        val btnMic = findViewById<android.widget.Button>(R.id.btnGrantMic)
        val missingMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        rowMic?.visibility = if (missingMic) View.VISIBLE else View.GONE
        btnMic?.setOnClickListener { requestRuntime(Manifest.permission.RECORD_AUDIO, 200) }

        val anyMissing = missingOverlay || missingContacts || missingCallLog || missingMic
        cardWarning.visibility = if (anyMissing) View.VISIBLE else View.GONE
    }

    fun applySubscriptionState() {
        val isPremium = SubscriptionManager.isPremiumUser(this) || PremiumManager.isUserPremium
        val iconRes = if (isPremium) R.drawable.ic_settings else R.drawable.ic_lock
        val tintColor = if (isPremium) "#88FFFFFF" else "#FFD700"
        val statusIds = listOf(
            R.id.imgContactForceStatus,
            R.id.imgP2PLinkStatus,
            R.id.imgGalleryStatus,
            R.id.imgStepRevealStatus,
            R.id.imgMoiLockStatus,
            R.id.imgExplosionStatus,
            R.id.imgToxicStatus,
            R.id.imgGhostStatus,
            R.id.imgRecoveryStatus,
            R.id.imgWallpaperMagicStatus
        )
        for (id in statusIds) {
            val img = findViewById<android.widget.ImageView>(id) ?: continue
            img.setImageResource(iconRes)
            img.setColorFilter(Color.parseColor(tintColor))
        }

        updatePremiumLockUI()
        updateWallpaperMagicIconStatus()
    }

    private fun updateWallpaperMagicIconStatus() {
        val isEnabled = (settingsManager.activeMagic == "WALLPAPER_MAGIC") || settingsManager.isWallpaperMagicEnabled || settingsManager.isVoiceListenerEnabled
        val tint = if (isEnabled) Color.parseColor("#00FFFF") else Color.parseColor("#88FFFFFF")
        findViewById<android.widget.ImageView>(R.id.imgWallpaperMagicIconLeft)?.setColorFilter(tint)
        findViewById<TextView>(R.id.txtWallpaperMagicTitleLeft)?.setTextColor(tint)
        findViewById<android.widget.ImageView>(R.id.imgWallpaperMagicStatus)?.setColorFilter(tint)
    }

    private fun updatePremiumLockUI() {
        val isPremium = SubscriptionManager.isPremiumUser(this) || PremiumManager.isUserPremium

        // 1. Theme Tab options: Remove 🔒 PRO when subscribed
        binding.rbDialer.text = if (isPremium) "Phone Dialer (Stealth Mode)" else "Phone Dialer 🔒 PRO (Stealth Mode)"
        binding.rbGallery.text = if (isPremium) "Gallery Theme (Prediction Mode)" else "Gallery Theme 🔒 PRO (Prediction Mode)"

        // 2. App Icons Tab: Remove 🔒 PRO when subscribed
        binding.txtIconCalendarTitle.text = if (isPremium) "Calendar" else "Calendar 🔒 PRO"
        binding.txtIconNotesTitle.text = if (isPremium) "Notes" else "Notes 🔒 PRO"
        binding.txtIconSettingsTitle.text = if (isPremium) "Settings" else "Settings 🔒 PRO"
        binding.txtIconMessagesTitle.text = if (isPremium) "Messages" else "Messages 🔒 PRO"
    }

    private fun updateAdvancedCardPremiumBadges() {
        applySubscriptionState()
    }
}
