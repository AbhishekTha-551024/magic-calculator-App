package com.magic.calculator

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import com.magic.calculator.feature.voice_wallpaper.data.voice.StealthVoiceService
import com.magic.calculator.feature.voice_wallpaper.data.voice.VoiceCaptureManager
import com.magic.calculator.feature.voice_wallpaper.data.wallpaper.WallpaperMagicManager
import com.magic.calculator.core.audio.SoundManager
import com.magic.calculator.core.logic.AuthManager
import com.magic.calculator.databinding.ActivityMainBinding
import com.magic.calculator.di.AppContainer
import com.magic.calculator.core.ui.util.MagicToast
import com.magic.calculator.core.haptic.HapticManager
import com.magic.calculator.core.logic.InteractiveCoachManager
import com.magic.calculator.core.logic.MagicEngine
import com.magic.calculator.core.logic.OnboardingManager
import com.magic.calculator.core.logic.PremiumManager
import com.magic.calculator.core.audio.MicPermission
import com.magic.calculator.core.logic.SettingsBus
import com.magic.calculator.core.logic.SettingsManager
import com.magic.calculator.core.logic.VoiceFeaturePolicy
import com.magic.calculator.core.logic.SubscriptionManager
import com.magic.calculator.feature.p2p.presentation.P2PLinkManager
import com.magic.calculator.feature.p2p.domain.model.P2PEventListener
import com.magic.calculator.feature.vault.presentation.GalleryActivity
import com.magic.calculator.feature.vault.presentation.PhotoViewerActivity
import com.magic.calculator.feature.applock.presentation.service.MoiLockService
import com.magic.calculator.core.sensor.SensorCoordinator
import com.magic.calculator.core.ui.calculator.CalculatorKeypadController
import com.magic.calculator.feature.auth.presentation.LoginActivity
import com.magic.calculator.feature.dialer.presentation.DialerController
import com.magic.calculator.core.ui.physics.PhysicsExplosionEngine
import com.magic.calculator.feature.calculator.presentation.viewmodel.MagicViewModel
import com.magic.calculator.feature.subscription.presentation.PremiumActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * MainActivity
 *
 * Primary calculator and dialer UI screen.
 * Hardware sensors are delegated to [SensorCoordinator].
 * Button scatter and tilt physics are delegated to [PhysicsExplosionEngine].
 * Keypad clicks and dialer modes are delegated to modular controllers.
 */
class MainActivity : AppCompatActivity(), SensorCoordinator.SensorEventListenerCallback {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MagicViewModel by viewModels()

    private val appContainer: AppContainer get() = (application as MagicApplication).appContainer
    private val settingsManager: SettingsManager by lazy { appContainer.settingsManager }
    private val soundManager: SoundManager by lazy { appContainer.soundManager }
    private val hapticManager: HapticManager by lazy { appContainer.hapticManager }
    private val magicEngine: MagicEngine by lazy { appContainer.magicEngine }

    private lateinit var sensorCoordinator: SensorCoordinator
    private lateinit var physicsEngine: PhysicsExplosionEngine
    private lateinit var dialerController: DialerController
    private lateinit var keypadController: CalculatorKeypadController

    private val PERMISSION_REQUEST_CODE = 100
    private var lastDisplayLength = 0
    private var isRestoringOrTransitioning = false
    private var lastLuckyActionTime = 0L
    private val LUCKY_COOLDOWN = 300L
    private var equalClickCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!AuthManager.isLoggedIn) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        } else {
            PremiumManager.startListening(this)
        }

        setupEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initSystems()

        if (settingsManager.appMode == 1) {
            if (!PremiumManager.isUserPremium) {
                settingsManager.appMode = 0
            }
        }

        if (settingsManager.appMode == 3) {
            if (!PremiumManager.isUserPremium) {
                settingsManager.appMode = 0
            } else {
                startActivity(Intent(this, GalleryActivity::class.java))
                finish()
                return
            }
        }

        if (settingsManager.appMode == 4) {
            if (!PremiumManager.isUserPremium) {
                settingsManager.appMode = 0
            } else {
                val serviceIntent = Intent(this, MoiLockService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
            }
        }

        setupObservers()
        setupControllers()

        checkPermissions()
        runIntroSequence()
        applyTheme()

        val alreadyRestored = intent.getBooleanExtra("premium_already_restored", false)
        if (alreadyRestored) {
            applySubscriptionState()
        } else {
            applySubscriptionState()
            SubscriptionManager.syncAndRestorePremiumStatus(this) { success, _ ->
                if (success) {
                    runOnUiThread { applySubscriptionState() }
                }
            }
        }

        OnboardingManager.syncFromFirestore(this) { _ ->
            runOnUiThread {
                if (OnboardingManager.shouldShowFreeOnboarding(this@MainActivity) &&
                    !InteractiveCoachManager.isTrainingActive) {
                    InteractiveCoachManager.startTraining()
                }
            }
        }

        if (OnboardingManager.shouldShowFreeOnboarding(this)) {
            InteractiveCoachManager.startTraining()
        }

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.layoutDialer.layoutContactDetails.visibility == View.VISIBLE) {
                    binding.layoutDialer.layoutContactDetails.visibility = View.GONE
                } else if (binding.layoutDialer.dialPadOverlay.visibility == View.VISIBLE) {
                    binding.layoutDialer.dialPadOverlay.visibility = View.GONE
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
    }

    fun applySubscriptionState() {
        val isPremium = SubscriptionManager.isPremiumUser(this) || PremiumManager.isUserPremium
        if (isPremium) {
            PremiumManager.setPremiumState(this, true)
        }
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

    private fun initSystems() {
        physicsEngine = PhysicsExplosionEngine(soundManager, hapticManager)
        sensorCoordinator = SensorCoordinator(this, settingsManager, lifecycleScope, this)
        setupP2PListener()
        observeMicSharing()
    }

    /**
     * Mic cooperation between the clap loop and speech recognition.
     *
     * - Levels from a live capture session are forwarded to the sensor coordinator, so a clap or
     *   finger snap still fires while ASR holds the microphone instead of a second `AudioRecord`
     *   competing for it.
     * - The trigger setting is observed rather than polled, so switching to or away from CLAP in
     *   SecretActivity takes effect the moment it is saved.
     */
    private fun observeMicSharing() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    appContainer.voiceCaptureManager.rms.collect { level ->
                        sensorCoordinator.onExternalAudioLevel(level)
                    }
                }
                launch {
                    SettingsBus.activeSensorTrigger.collect { trigger ->
                        if (trigger == "CLAP") sensorCoordinator.startAudioMonitoring()
                        else sensorCoordinator.stopAudioMonitoring()
                    }
                }
            }
        }
    }

    private fun setupControllers() {
        dialerController = DialerController(
            activity = this,
            binding = binding.layoutDialer,
            settingsManager = settingsManager,
            soundManager = soundManager,
            hapticManager = hapticManager,
            magicEngine = magicEngine,
            onLuckyTrigger = { startLuckyMagic() }
        )
        dialerController.init()

        keypadController = CalculatorKeypadController(
            binding = binding,
            viewModel = viewModel,
            soundManager = soundManager,
            hapticManager = hapticManager,
            physicsEngine = physicsEngine,
            onEqualClicked = { handleEqualClick() },
            onEqualLongClicked = { handleEqualLongClick() },
            onPanicReset = { performPanicReset() },
            onClearClicked = { handleClearClick() },
            onBackspaceClicked = { handleBackspaceClick() }
        )
        keypadController.init()
        setupSwipeToDelete()
    }

    private fun setupP2PListener() {
        val p2p = P2PLinkManager.getInstance()
        if (AuthManager.currentUid != null) {
            if (p2p.myMagicId.isEmpty()) {
                p2p.initialize { }
            } else {
                p2p.startListeningForRequests()
            }
        }
        p2p.listener = object : P2PEventListener {
            override fun onIncomingRequest(fromMagicId: String, requestDocId: String) {
                runOnUiThread {
                    android.app.AlertDialog.Builder(this@MainActivity)
                        .setTitle("Incoming Magic Link")
                        .setMessage("Connection request from $fromMagicId.\nAccept to sync displays?")
                        .setPositiveButton("Accept") { _, _ ->
                            p2p.acceptRequest(requestDocId, fromMagicId)
                            MagicToast.show(this@MainActivity, "Connected to $fromMagicId")
                        }
                        .setNegativeButton("Reject") { _, _ ->
                            p2p.declineRequest(requestDocId)
                        }
                        .setCancelable(false)
                        .show()
                }
            }
            override fun onConnected(partnerMagicId: String, sessionId: String) {
                settingsManager.isP2PEnabled = true
                settingsManager.p2pPartnerMagicId = partnerMagicId
                settingsManager.p2pSessionId = sessionId
            }
            override fun onDisconnected() {
                settingsManager.isP2PEnabled = false
                settingsManager.p2pSessionId = ""
                settingsManager.p2pPartnerMagicId = ""
            }
            override fun onRemoteDisplayUpdate(display: String, formula: String) {
                runOnUiThread {
                    if (settingsManager.appMode != 2) {
                        viewModel.setRemoteDisplay(display, formula)
                    }
                }
            }
            override fun onRemoteCommand(command: String) {
                executeP2PCommand(command)
            }
            override fun onError(message: String) { }
        }
    }

    // ── SensorCoordinator Callbacks ─────────────────────────────────────────

    override fun onMagicTrigger(triggerType: String) {
        if (InteractiveCoachManager.isTrainingActive) {
            if (InteractiveCoachManager.currentStep.value != InteractiveCoachManager.TrainingStep.STEP_10_PERFORM_WAVE) {
                return
            }
        }
        if (settingsManager.isWallpaperMagicEnabled) {
            val method = settingsManager.wallpaperTriggerMethod
            if ((method == "PROXIMITY_WAVE" && triggerType == "PROXIMITY") ||
                (method == "FACE_DOWN" && triggerType == "FACE_DOWN")) {
                triggerWallpaperForce()
                return
            }
        }
        triggerHardwareMagic()
    }

    override fun onStepRevealTrigger(triggerType: String) {
        if (InteractiveCoachManager.isTrainingActive) return
        if (settingsManager.isWallpaperMagicEnabled) {
            val method = settingsManager.wallpaperTriggerMethod
            if ((method == "PROXIMITY_WAVE" && triggerType == "PROXIMITY") ||
                (method == "FACE_DOWN" && triggerType == "FACE_DOWN")) {
                triggerWallpaperForce()
            }
        }
        viewModel.revealStepWithTrigger(triggerType)
    }

    override fun onLuckyTrigger(value: Int) {
        if (InteractiveCoachManager.isTrainingActive) return
        setLuckyDisplay(value)
    }

    override fun onExplosionTrigger(triggerType: String) {
        if (InteractiveCoachManager.isTrainingActive) return
        if (isRestoringOrTransitioning) return
        if (!physicsEngine.isButtonsExploded) {
            explodeButtons()
        } else {
            triggerGalleryTransition()
        }
    }

    override fun onGravityChanged(gx: Float, gy: Float) {
        physicsEngine.gravityX = gx
        physicsEngine.gravityY = gy
    }

    override fun onShakeCount(count: Int) {
        if (InteractiveCoachManager.isTrainingActive) return
        if (count >= 1) {
            if (viewModel.isWaitingForShake && count >= 3) {
                viewModel.jumpLastDigitToEnd()
                hapticManager.magicVibrate()
                soundManager.playMagicSwoosh()
            } else if (sensorCoordinator.isLuckyActive) {
                setLuckyDisplay(6)
            } else if (settingsManager.activeSensorTrigger == "SHAKE") {
                triggerHardwareMagic()
            }
        }
    }

    override fun onHapticFeedback(shakeLevel: Int) {
        hapticManager.shakeFeedback(shakeLevel)
    }

    // ── Trigger Hardware Magic ──────────────────────────────────────────────

    private fun triggerHardwareMagic() {
        if (settingsManager.appMode == 2) return
        if (settingsManager.activeSensorTrigger == "NONE") return

        // Interactive Coach Guard: In Stage 3 live performance, magic flip MUST ONLY activate
        // at STEP_10_PERFORM_WAVE (after user has typed '66' and pressed '=').
        if (InteractiveCoachManager.isTrainingActive) {
            if (InteractiveCoachManager.currentStep.value != InteractiveCoachManager.TrainingStep.STEP_10_PERFORM_WAVE) {
                return
            }
        }

        val activeMagic = settingsManager.activeMagic
        val isPremiumTrick = activeMagic in listOf("TOXIC_TIME", "GHOST_TYPING", "REAPPEARING_RESULT", "STEP_REVEAL", "CONTACT_FORCE", "TOXIC_GALLERY")
        val isSubscribed = SubscriptionManager.isPremiumUser(this) || PremiumManager.isUserPremium
        if (isPremiumTrick && !isSubscribed) {
            settingsManager.activeMagic = "NONE"
            startActivity(Intent(this, PremiumActivity::class.java))
            MagicToast.show(this, "Premium features disabled. Please purchase premium.", isLong = true)
            return
        }

        if (settingsManager.isDialerThemeActive) {
            if (activeMagic == "DIGIT_FLIP") {
                if (dialerController.dialInput == settingsManager.customFlipTrigger && !sensorCoordinator.flipAlreadyPerformed) {
                    sensorCoordinator.flipAlreadyPerformed = true
                    dialerController.performDialerMagic(MagicEngine.TrickType.DIGIT_FLIP)
                }
            } else if (activeMagic == "PHONE_NUMBER_FORCE") {
                dialerController.performDialerMagic(MagicEngine.TrickType.PHONE_NUMBER_FORCE)
            }
        } else {
            val currentDisplay = viewModel.displayText.value ?: ""
            if (activeMagic == "DIGIT_FLIP") {
                if (currentDisplay == settingsManager.customFlipTrigger && !sensorCoordinator.flipAlreadyPerformed) {
                    sensorCoordinator.flipAlreadyPerformed = true
                    viewModel.onHardwareTrigger()
                    hapticManager.magicVibrate()
                    soundManager.playMagicSwoosh()
                }
            } else if (activeMagic == "GHOST_TYPING") {
                triggerGhostReveal()
            } else {
                viewModel.onHardwareTrigger()
                hapticManager.magicVibrate()
                soundManager.playMagicSwoosh()
            }
        }
    }

    private fun setupObservers() {
        viewModel.displayText.observe(this) { text ->
            binding.tvDisplay.text = text
            lastDisplayLength = text.length

            val p2p = P2PLinkManager.getInstance()
            if (p2p.isConnected && p2p.isMirrorEnabled && !viewModel.isReceivingRemote) {
                val formula = viewModel.formulaText.value ?: ""
                p2p.syncDisplay(text, formula)
            }

            if (settingsManager.activeMagic == "TOXIC_GALLERY") {
                val parts = text.split(".")
                if (parts.size == 2) {
                    val hour = parts[0].toIntOrNull()
                    val minStr = parts[1]
                    if (hour != null && hour in 0..23 && minStr.isNotEmpty() && minStr.length <= 2) {
                        val minute = minStr.toIntOrNull()
                        if (minute != null && minute in 0..59) {
                            val formattedMin = if (minStr.length == 1) "${minute}0" else String.format("%02d", minute)
                            val formattedHour = if (parts[0].length == 2) String.format("%02d", hour) else hour.toString()
                            settingsManager.forcedGalleryTime = "$formattedHour:$formattedMin"
                        }
                    }
                } else if (text.all { it.isDigit() }) {
                    if (text.length == 3) {
                        val hour = text.substring(0, 1).toIntOrNull()
                        val minute = text.substring(1, 3).toIntOrNull()
                        if (hour != null && minute != null && minute in 0..59) {
                            settingsManager.forcedGalleryTime = "$hour:${String.format("%02d", minute)}"
                        }
                    } else if (text.length == 4) {
                        val hour = text.substring(0, 2).toIntOrNull()
                        val minute = text.substring(2, 4).toIntOrNull()
                        if (hour != null && hour in 0..23 && minute != null && minute in 0..59) {
                            settingsManager.forcedGalleryTime = "$hour:${String.format("%02d", minute)}"
                        }
                    }
                }
            }

            val trigger = settingsManager.customFlipTrigger
            val result  = settingsManager.customFlipResult
            if (text != trigger && text != result && (text == "0" || text.isEmpty())) {
                sensorCoordinator.flipAlreadyPerformed = false
                sensorCoordinator.resetFlipState()
            }

            val currentStep = InteractiveCoachManager.currentStep.value
            if (currentStep == InteractiveCoachManager.TrainingStep.STEP_1_TAP_7 && text.endsWith("7")) {
                InteractiveCoachManager.notifyDigitPressed("7")
            } else if (currentStep == InteractiveCoachManager.TrainingStep.STEP_2_TAP_4 && text.endsWith("4")) {
                InteractiveCoachManager.notifyDigitPressed("4")
            } else if (currentStep == InteractiveCoachManager.TrainingStep.STEP_3_TAP_1 && text.endsWith("1")) {
                InteractiveCoachManager.notifyDigitPressed("1")
            } else if (currentStep == InteractiveCoachManager.TrainingStep.STEP_8_PERFORM_66 && (text == "66" || text.endsWith("66"))) {
                InteractiveCoachManager.setStep(InteractiveCoachManager.TrainingStep.STEP_9_PERFORM_EQUAL)
            } else if (currentStep == InteractiveCoachManager.TrainingStep.STEP_10_PERFORM_WAVE && (text == "99" || text == result)) {
                InteractiveCoachManager.notifyMagicFlipped(this)
            }
        }
        viewModel.formulaText.observe(this) { formula ->
            binding.tvFormula.text = formula
        }
        viewModel.secretUnlocked.observe(this) {
            if (it) startActivity(Intent(this, SecretActivity::class.java))
        }
        viewModel.newGhostDigit.observe(this) { ghost ->
            if (ghost == null) {
                binding.ghostContainer.removeAllViews()
            } else {
                addGhostDigitToScreen(ghost)
            }
        }
        viewModel.magicTriggered.observe(this) { trick ->
            if (trick != MagicEngine.TrickType.NONE) {
                hapticManager.magicVibrate()
                soundManager.playMagicSwoosh()
                viewModel.onMagicConsumed()
                if (trick == MagicEngine.TrickType.DIGIT_FLIP) {
                    InteractiveCoachManager.notifyMagicFlipped(this)
                }
            }
        }

        InteractiveCoachManager.currentStep.observe(this) { step ->
            updateCoachUI(step)
        }
    }

    private var highlightedView: View? = null

    private fun updateCoachUI(step: InteractiveCoachManager.TrainingStep) {
        val coachCard = binding.layoutCoach.cardCoachOverlay
        val tvTitle = binding.layoutCoach.tvCoachTitle
        val tvInstruction = binding.layoutCoach.tvCoachInstruction
        val btnSkip = binding.layoutCoach.btnCoachSkip
        btnSkip.visibility = View.GONE
        btnSkip.setOnClickListener {
            // Mark as skipped by new user so hint banner shows
            InteractiveCoachManager.skipTraining(this, newUserSkipped = true)
            showUnlockHintBanner()
        }

        resetButtonHighlights()

        if (step == InteractiveCoachManager.TrainingStep.OFF) {
            coachCard.visibility = View.GONE
            setKeypadButtonsLock(null)
            return
        }

        coachCard.visibility = View.VISIBLE
        tvTitle.text = step.title

        when (step) {
            InteractiveCoachManager.TrainingStep.STEP_1_TAP_7 -> {
                setKeypadButtonsLock(binding.btn7)
                highlightButton(binding.btn7)
                showPointerAbove(binding.btn7, "👇 Tap 7")
                tvInstruction.text = "👉 TAP THE HIGHLIGHTED BUTTON '7'"
            }
            InteractiveCoachManager.TrainingStep.STEP_2_TAP_4 -> {
                setKeypadButtonsLock(binding.btn4)
                highlightButton(binding.btn4)
                showPointerAbove(binding.btn4, "👇 Tap 4")
                tvInstruction.text = "👉 TAP THE HIGHLIGHTED BUTTON '4'"
            }
            InteractiveCoachManager.TrainingStep.STEP_3_TAP_1 -> {
                setKeypadButtonsLock(binding.btn1)
                highlightButton(binding.btn1)
                showPointerAbove(binding.btn1, "👇 Tap 1")
                tvInstruction.text = "👉 TAP THE HIGHLIGHTED BUTTON '1'"
            }
            InteractiveCoachManager.TrainingStep.STEP_4_TAP_EQUAL -> {
                setKeypadButtonsLock(binding.btnEqual)
                highlightButton(binding.btnEqual)
                showPointerAbove(binding.btnEqual, "👇 Tap = to Unlock")
                tvInstruction.text = "👉 TAP THE HIGHLIGHTED BUTTON '=' TO UNLOCK"
            }
            InteractiveCoachManager.TrainingStep.STEP_8_PERFORM_66 -> {
                viewModel.onClearClick()
                dialerController.setDialInput("0")
                sensorCoordinator.flipAlreadyPerformed = false
                sensorCoordinator.resetFlipState()
                setKeypadButtonsLock(binding.btn6)
                highlightButton(binding.btn6)
                showPointerAbove(binding.btn6, "👇 Type 66")
                tvInstruction.text = "👉 TYPE '66' ON THE KEYPAD"
            }
            InteractiveCoachManager.TrainingStep.STEP_9_PERFORM_EQUAL -> {
                setKeypadButtonsLock(binding.btnEqual)
                highlightButton(binding.btnEqual)
                showPointerAbove(binding.btnEqual, "👇 Tap =")
                tvInstruction.text = "👉 TAP '=' TO ARM MAGIC"
            }
            InteractiveCoachManager.TrainingStep.STEP_10_PERFORM_WAVE -> {
                lockAllKeypadButtons()
                resetButtonHighlights()
                tvTitle.text = "Digital Flip Performance"
                tvInstruction.text = "👋 Wave your hand over top proximity sensor to flip 66 into 99!"
            }
            InteractiveCoachManager.TrainingStep.TRAINING_COMPLETED -> {
                setKeypadButtonsLock(null)
                hidePointer()
                tvTitle.text = "🎉 Training Complete!"
                tvInstruction.text = "✨ Digital Flip Mastered! 66 flipped to 99 without touching the screen!"
                hapticManager.magicVibrate()
                soundManager.playMagicSwoosh()
                coachCard.postDelayed({
                    InteractiveCoachManager.skipTraining(this)
                }, 4000)
            }
            else -> {
                setKeypadButtonsLock(null)
                hidePointer()
                tvInstruction.text = step.instruction
            }
        }
    }

    private fun lockAllKeypadButtons() {
        val allButtons = listOf(
            binding.btn0, binding.btn1, binding.btn2, binding.btn3, binding.btn4,
            binding.btn5, binding.btn6, binding.btn7, binding.btn8, binding.btn9,
            binding.btnDot, binding.btnAdd, binding.btnSub, binding.btnMul,
            binding.btnDiv, binding.btnEqual, binding.btnPercent, binding.btnC,
            binding.btnBackspace, binding.btnPlusMinus
        )
        allButtons.forEach { btn ->
            btn.isEnabled = false
            btn.alpha = 0.35f
        }
    }

    private fun setKeypadButtonsLock(activeButton: View?) {
        val allButtons = listOf(
            binding.btn0, binding.btn1, binding.btn2, binding.btn3, binding.btn4,
            binding.btn5, binding.btn6, binding.btn7, binding.btn8, binding.btn9,
            binding.btnDot, binding.btnAdd, binding.btnSub, binding.btnMul,
            binding.btnDiv, binding.btnEqual, binding.btnPercent, binding.btnC,
            binding.btnBackspace, binding.btnPlusMinus
        )

        if (activeButton == null) {
            allButtons.forEach { btn ->
                btn.isEnabled = true
                btn.alpha = 1.0f
            }
        } else {
            allButtons.forEach { btn ->
                val isTarget = (btn == activeButton)
                btn.isEnabled = isTarget
                btn.alpha = if (isTarget) 1.0f else 0.28f
            }
        }
    }

    private var highlightedButton: android.widget.Button? = null
    private var originalBgTint: android.content.res.ColorStateList? = null
    private var originalTextColor: Int = Color.WHITE

    private fun highlightButton(btn: android.widget.Button) {
        resetButtonHighlights()

        highlightedButton = btn
        originalBgTint = btn.backgroundTintList
        originalTextColor = btn.currentTextColor

        // High contrast glowing neon cyan background with bold black text!
        btn.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#00E5FF"))
        btn.setTextColor(Color.parseColor("#000000"))
        btn.setTypeface(btn.typeface, Typeface.BOLD)

        // Continuous smooth pulsating scale animation
        btn.animate()
            .scaleX(1.22f)
            .scaleY(1.22f)
            .setDuration(600)
            .setInterpolator(android.view.animation.CycleInterpolator(30f))
            .start()
    }

    private fun showPointerAbove(targetView: View, text: String) {
        val pointerLayout = binding.layoutCoachPointer
        val tvBubble = binding.tvPointerBubble

        tvBubble.text = text
        pointerLayout.visibility = View.VISIBLE

        targetView.post {
            val locTarget = IntArray(2)
            val locMain = IntArray(2)
            targetView.getLocationOnScreen(locTarget)
            binding.mainLayout.getLocationOnScreen(locMain)

            pointerLayout.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
            val pointerW = pointerLayout.measuredWidth
            val pointerH = pointerLayout.measuredHeight

            val targetCenterX = (locTarget[0] - locMain[0]) + (targetView.width / 2f)
            val targetTopY = (locTarget[1] - locMain[1]).toFloat()

            pointerLayout.x = targetCenterX - (pointerW / 2f)
            pointerLayout.y = targetTopY - pointerH - 8f

            // Start continuous bobbing bounce animation
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

    private fun hidePointer() {
        binding.layoutCoachPointer.clearAnimation()
        binding.layoutCoachPointer.visibility = View.GONE
    }

    private fun resetButtonHighlights() {
        hidePointer()
        highlightedButton?.let { btn ->
            btn.animate().cancel()
            btn.scaleX = 1.0f
            btn.scaleY = 1.0f
            btn.backgroundTintList = originalBgTint ?: android.content.res.ColorStateList.valueOf(Color.parseColor("#2A2A38"))
            btn.setTextColor(originalTextColor)
        }
        highlightedButton = null
    }

    private fun handleEqualClick() {
        val calcDisplay = viewModel.displayText.value ?: ""
        val currentStep = InteractiveCoachManager.currentStep.value

        if (viewModel.checkSecretUnlock(settingsManager.secretUnlockCode)) {
            soundManager.playMagicSwoosh()
            hapticManager.magicVibrate()
            viewModel.onClearClick()
            dialerController.setDialInput("0")
            // Hide the unlock hint banner — user has successfully unlocked backstage!
            hideUnlockHintBanner()
            if (currentStep == InteractiveCoachManager.TrainingStep.STEP_4_TAP_EQUAL) {
                InteractiveCoachManager.notifyEqualPressed()
            }
            return
        }

        if (currentStep == InteractiveCoachManager.TrainingStep.STEP_9_PERFORM_EQUAL) {
            InteractiveCoachManager.notifyEqualPressed()
        }

        val activeMagic = settingsManager.activeMagic
        if ((dialerController.dialInput == "777" || calcDisplay == "777") && activeMagic == "LUCKY") {
            startLuckyMagic()
            equalClickCount = 0
        } else if (activeMagic == "TOXIC_TIME") {
            val toxicValue = calculateToxicValue()
            viewModel.onHardwareTrigger(toxicValue)
            equalClickCount = 0
        } else if (activeMagic == "GHOST_TYPING") {
            triggerGhostReveal()
            equalClickCount = 0
        } else {
            viewModel.onEqualClick(settingsManager.secretUnlockCode)
            equalClickCount++
            if (equalClickCount >= 2 && activeMagic == "REAPPEARING_RESULT") {
                hapticManager.buttonTick()
            }
        }
        if (settingsManager.isWallpaperMagicEnabled && !InteractiveCoachManager.isTrainingActive) {
            triggerWallpaperForce()
        }
        soundManager.playButtonTick()
        hapticManager.buttonTick()
    }

    private fun handleEqualLongClick(): Boolean {
        if (settingsManager.activeMagic == "TOXIC_GALLERY") {
            hapticManager.magicVibrate()
            soundManager.playMagicSwoosh()
            settingsManager.appMode = 3
            val intent = Intent(this, GalleryActivity::class.java)
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
            return true
        }
        return false
    }

    private fun handleClearClick() {
        if (settingsManager.isWallpaperMagicEnabled && settingsManager.wallpaperTriggerMethod == "AC_CLEAR") {
            triggerWallpaperForce()
        }
        viewModel.onClearClick()
        equalClickCount = 0
        isRestoringOrTransitioning = false
        if (sensorCoordinator.isLuckyActive) {
            dialerController.setDialInput("0")
            binding.tvDisplay.text = "0"
            viewModel.forceDisplay("0")
        }
        soundManager.playButtonTick()
        hapticManager.buttonTick()
    }

    private fun handleBackspaceClick() {
        viewModel.onBackspaceClick()
        if (sensorCoordinator.isLuckyActive) {
            dialerController.setDialInput("0")
            binding.tvDisplay.text = "0"
            viewModel.forceDisplay("0")
        }
        soundManager.playButtonTick()
        hapticManager.buttonTick()
    }

    private fun performPanicReset() {
        settingsManager.activeMagic = "NONE"
        settingsManager.isContactForceEnabled = false
        settingsManager.isExplosionEnabled = false
        viewModel.wipeMagicMemory()
        dialerController.setDialInput("0")
        equalClickCount = 0
        if (sensorCoordinator.isLuckyActive) exitLuckyMode()
        sensorCoordinator.resetFlipState()
        sensorCoordinator.flipAlreadyPerformed = false
        isRestoringOrTransitioning = false
        hapticManager.magicVibrate()
    }

    private fun exitLuckyMode() {
        sensorCoordinator.isLuckyActive = false
        dialerController.setDialInput("")
        runOnUiThread {
            binding.tvDisplay.text = "0"
            viewModel.onClearClick()
            hapticManager.magicVibrate()
            soundManager.playButtonTick()
        }
    }

    private fun startLuckyMagic() {
        sensorCoordinator.isLuckyActive = true
        dialerController.setDialInput("0")
        runOnUiThread {
            binding.tvDisplay.text = "0"
            viewModel.forceDisplay("0")
            hapticManager.magicVibrate()
            soundManager.playMagicSwoosh()
        }
    }

    private fun setLuckyDisplay(value: Int) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastLuckyActionTime < LUCKY_COOLDOWN) return
        lastLuckyActionTime = currentTime
        runOnUiThread {
            val valStr = value.toString()
            dialerController.setDialInput(valStr)
            binding.tvDisplay.text = valStr
            viewModel.forceDisplay(valStr)
            hapticManager.buttonTick()
            soundManager.playMagicSwoosh()
        }
    }

    private fun calculateToxicValue(): String {
        return when (settingsManager.toxicMode) {
            0 -> SimpleDateFormat("HHmm", Locale.getDefault()).format(Date())
            1 -> SimpleDateFormat("ddMM", Locale.getDefault()).format(Date())
            2 -> {
                val bm = getSystemService(BATTERY_SERVICE) as android.os.BatteryManager
                bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY).toString()
            }
            else -> "0"
        }
    }

    private fun triggerGhostReveal() {
        val delay = settingsManager.ghostTypingDelay * 1000L
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            viewModel.onHardwareTrigger()
            hapticManager.magicVibrate()
            soundManager.playMagicSwoosh()
        }, delay)
    }

    private fun checkPermissions() {
        val perms = arrayOf(android.Manifest.permission.READ_CALL_LOG, android.Manifest.permission.READ_CONTACTS)
        if (perms.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
            ActivityCompat.requestPermissions(this, perms, PERMISSION_REQUEST_CODE)
        } else {
            dialerController.loadCallLogs()
            dialerController.loadContacts()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            dialerController.loadCallLogs()
            dialerController.loadContacts()
        }
    }

    private fun applyTheme() {
        val mode = settingsManager.appMode
        if (mode == 1) {
            binding.displayContainer.visibility  = View.GONE
            binding.buttonGrid.visibility        = View.GONE
            binding.layoutDialer.root.visibility = View.VISIBLE
            applyDialerTheme()
        } else {
            binding.displayContainer.visibility  = View.VISIBLE
            binding.buttonGrid.visibility        = View.VISIBLE
            binding.layoutDialer.root.visibility = View.GONE
            window.statusBarColor = Color.BLACK
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
        }
    }

    private fun applyDialerTheme() {
        val isDark = settingsManager.isDialerThemeDark
        val bgCol = if (isDark) Color.parseColor("#121212") else Color.parseColor("#FDFBF5")
        val primaryColor = if (isDark) Color.WHITE else Color.parseColor("#212121")
        val secondaryColor = if (isDark) Color.parseColor("#BBBBBB") else Color.parseColor("#757575")

        binding.layoutDialer.dialerRoot.setBackgroundColor(bgCol)
        binding.layoutDialer.dialerHeader.setBackgroundColor(bgCol)
        binding.layoutDialer.bottomNav.setBackgroundColor(bgCol)
        binding.layoutDialer.tvMainTitle.setTextColor(primaryColor)

        binding.layoutDialer.ivSelect.setColorFilter(primaryColor)
        binding.layoutDialer.ivMore.setColorFilter(primaryColor)

        val tintList = android.content.res.ColorStateList.valueOf(secondaryColor)
        binding.layoutDialer.bottomNav.itemIconTintList = tintList
        binding.layoutDialer.bottomNav.itemTextColor = tintList

        binding.layoutDialer.dialPadOverlay.findViewById<View>(R.id.vDimmer)?.setBackgroundColor(
            if (isDark) Color.parseColor("#44000000") else Color.parseColor("#05000000")
        )
        val dialPadContainer = binding.layoutDialer.dialPadOverlay.getChildAt(1) as? ConstraintLayout
        dialPadContainer?.setBackgroundColor(bgCol)
        binding.layoutDialer.tvDialNumber.setTextColor(primaryColor)

        val dialButtonsIds = listOf(
            R.id.dial0, R.id.dial1, R.id.dial2, R.id.dial3, R.id.dial4,
            R.id.dial5, R.id.dial6, R.id.dial7, R.id.dial8, R.id.dial9,
            R.id.dialStar, R.id.dialHash
        )
        dialButtonsIds.forEach { id ->
            val btnLayout = binding.layoutDialer.dialPadOverlay.findViewById<android.widget.LinearLayout>(id)
            if (btnLayout != null) {
                val txtNum = btnLayout.getChildAt(0) as? TextView
                txtNum?.setTextColor(primaryColor)
                val txtLet = btnLayout.getChildAt(1) as? TextView
                if (txtLet != null) {
                    txtLet.setTextColor(secondaryColor)
                } else {
                    val imgLet = btnLayout.getChildAt(1) as? android.widget.ImageView
                    imgLet?.setColorFilter(secondaryColor)
                }
            }
        }

        binding.layoutDialer.btnDialMenu.setColorFilter(secondaryColor)
        binding.layoutDialer.btnDialBackspace.setColorFilter(secondaryColor)

        binding.layoutDialer.layoutContactDetails.setBackgroundColor(bgCol)
        binding.layoutDialer.detailsToolbar.setBackgroundColor(bgCol)
        binding.layoutDialer.btnDetailsBack.setColorFilter(primaryColor)
        binding.layoutDialer.btnDetailsEdit.setColorFilter(primaryColor)
        binding.layoutDialer.layoutContactDetails.findViewById<TextView>(R.id.tvDetailsName)?.setTextColor(primaryColor)
        binding.layoutDialer.tvDetailsPhone.setTextColor(primaryColor)
        binding.layoutDialer.tvDetailsCarrier.setTextColor(secondaryColor)

        binding.layoutDialer.tvDetailsMessageLabel.setTextColor(secondaryColor)
        binding.layoutDialer.tvDetailsCallLabel.setTextColor(secondaryColor)
        binding.layoutDialer.tvDetailsVideoLabel.setTextColor(secondaryColor)

        binding.layoutDialer.vDetailsDivider.setBackgroundColor(
            if (isDark) Color.parseColor("#333333") else Color.parseColor("#EEEEEE")
        )

        binding.layoutDialer.tvDetailsCallLogsTitle.setTextColor(primaryColor)
        binding.layoutDialer.tvDetailsMoreLink.setTextColor(secondaryColor)

        binding.layoutDialer.tvLogType1.setTextColor(primaryColor)
        binding.layoutDialer.tvLogType2.setTextColor(primaryColor)
        binding.layoutDialer.tvLogDuration1.setTextColor(secondaryColor)
        binding.layoutDialer.tvLogDuration2.setTextColor(secondaryColor)
        binding.layoutDialer.tvLogTime1.setTextColor(secondaryColor)
        binding.layoutDialer.tvLogTime2.setTextColor(secondaryColor)

        window.statusBarColor = bgCol
        window.navigationBarColor = bgCol
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = !isDark
        controller.isAppearanceLightNavigationBars = !isDark
    }

    private fun runIntroSequence() {
        binding.root.alpha = 0f
        binding.root.animate().alpha(1f).setDuration(500).start()
    }

    private fun setupSwipeToDelete() {
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vX: Float, vY: Float): Boolean {
                if (abs(vX) > abs(vY) && abs(vX) > 200) {
                    if (binding.tvDisplay.text == "0" || binding.tvDisplay.text.isEmpty()) {
                        if (settingsManager.isExplosionEnabled) explodeButtons()
                    } else {
                        viewModel.onBackspaceClick()
                        hapticManager.buttonTick()
                        soundManager.playButtonTick()
                    }
                    return true
                }
                return false
            }
        })

        binding.tvDisplay.setOnTouchListener { _, event ->
            if (settingsManager.activeMagic == "STEP_REVEAL") {
                if (event.action == MotionEvent.ACTION_DOWN) {
                    val digit = viewModel.peekNextDigit()
                    if (digit != null) {
                        val isTopArea = event.rawY < resources.displayMetrics.heightPixels / 4
                        viewModel.revealStepWithTrigger("TOUCH", event.rawX, event.rawY, isTop = isTopArea)
                        soundManager.playButtonTick()
                        hapticManager.buttonTick()
                        return@setOnTouchListener true
                    }
                }
            }
            gestureDetector.onTouchEvent(event)
            true
        }

        binding.mainLayout.setOnTouchListener { _, event ->
            if (settingsManager.activeMagic == "STEP_REVEAL" && event.action == MotionEvent.ACTION_DOWN) {
                val isTopArea = event.rawY < resources.displayMetrics.heightPixels / 4
                if (isTopArea) {
                    val digit = viewModel.peekNextDigit()
                    if (digit != null) {
                        viewModel.revealStepWithTrigger("TOUCH", event.rawX, event.rawY, isTop = true)
                        soundManager.playButtonTick()
                        hapticManager.buttonTick()
                        return@setOnTouchListener true
                    }
                }
            }
            false
        }

        binding.tvDisplay.setOnLongClickListener {
            val activeMagic = settingsManager.activeMagic
            if (activeMagic == "STEP_REVEAL" || activeMagic == "REAPPEARING_RESULT") {
                viewModel.startStepReveal()
                soundManager.playMagicSwoosh()
                hapticManager.magicVibrate()
                true
            } else false
        }
    }

    private fun applySettings() {
        soundManager.setVolume(settingsManager.soundVolume)
        hapticManager.intensityScale = settingsManager.hapticIntensity / 100f
        // Reading republishes it, so the clap loop sees the stored trigger even when this process
        // never wrote it (fresh launch, or the change was made on another SettingsManager instance).
        SettingsBus.publishActiveSensorTrigger(settingsManager.activeSensorTrigger)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (sensorCoordinator.isLuckyActive) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP)   { setLuckyDisplay(3); return true }
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) { setLuckyDisplay(4); return true }
        }

        if (settingsManager.isWallpaperMagicEnabled) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                if (event?.repeatCount == 0) {
                    toggleVolumeVoiceCapture()
                }
                return true
            }
        }

        val activeTrigger = settingsManager.activeSensorTrigger
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP   && activeTrigger == "VOL_UP")   { triggerHardwareMagic(); return true }
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && activeTrigger == "VOL_DOWN") { triggerHardwareMagic(); return true }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (sensorCoordinator.isLuckyActive && (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) return true
        if (settingsManager.isWallpaperMagicEnabled) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                return true
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onResume() {
        super.onResume()
        isRestoringOrTransitioning = false
        applySettings()
        applyTheme()
        applySubscriptionState()

        // Stop any stale background voice service (e.g. from previous session crash) BEFORE the
        // sensor loop takes the microphone. The old order grabbed the mic and only then released
        // the service's SpeechRecognizer, starving it on every return to the app.
        StealthVoiceService.stop(this)
        sensorCoordinator.resume()

        OnboardingManager.syncFromFirestore(this) { _ ->
            runOnUiThread {
                if (OnboardingManager.shouldShowFreeOnboarding(this@MainActivity) &&
                    !InteractiveCoachManager.isTrainingActive) {
                    InteractiveCoachManager.startTraining()
                }
            }
        }
    }

    private fun explodeButtons() {
        physicsEngine.explodeButtons(
            buttonGrid = binding.buttonGrid,
            tvDisplay = binding.tvDisplay,
            tvFormula = binding.tvFormula,
            mainLayout = binding.mainLayout
        )
    }

    private fun restoreButtons() {
        viewModel.isStepRevealActive = false
        physicsEngine.restoreButtons(
            buttonGrid = binding.buttonGrid,
            tvDisplay = binding.tvDisplay,
            tvFormula = binding.tvFormula
        )
    }

    private fun triggerGalleryTransition() {
        if (!physicsEngine.isButtonsExploded) return
        if (isRestoringOrTransitioning) return
        if (System.currentTimeMillis() - physicsEngine.lastExplodeTime < 1500L) return
        isRestoringOrTransitioning = true
        restoreButtons()
        hapticManager.magicVibrate()
        soundManager.playMagicSwoosh()
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            settingsManager.appMode = 3
            val activeMagic = settingsManager.activeMagic
            val imageList = try {
                assets.list("gallery")?.filter {
                    val lower = it.lowercase()
                    val isImage = lower.endsWith(".jpg") || lower.endsWith(".png") ||
                                  lower.endsWith(".jpeg") || lower.endsWith(".webp")
                    if (activeMagic == "TOXIC_GALLERY") {
                        isImage
                    } else {
                        isImage && it != "prediction_board.png"
                    }
                }?.sorted() ?: listOf()
            } catch (e: Exception) { listOf() }
            if (imageList.isNotEmpty()) {
                val targetImage = "screenshort.jpg"
                val targetIndex = imageList.indexOf(targetImage)

                val finalImageName = if (targetIndex != -1) targetImage else imageList.last()
                val finalPosition = if (targetIndex != -1) targetIndex + 1 else imageList.size

                val intent = Intent(this, PhotoViewerActivity::class.java).apply {
                    putExtra("EXTRA_IMAGE_NAME", finalImageName)
                    putExtra("EXTRA_POSITION", finalPosition)
                    putExtra("EXTRA_HIDE_BARS_INITIALLY", true)
                }
                startActivity(intent)
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                finish()
            } else {
                isRestoringOrTransitioning = false
            }
        }, 20L)
    }

    private fun addGhostDigitToScreen(ghost: MagicViewModel.GhostDigit) {
        val tv = TextView(this).apply {
            text = ghost.digit
            setTextColor(Color.WHITE)
            textSize = 96f
            alpha = 1.0f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            rotation = 45f
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        binding.ghostContainer.addView(tv)

        tv.post {
            tv.x = ghost.x - (tv.width / 2)
            tv.y = ghost.y - (tv.height / 2)

            tv.scaleX = 0.5f; tv.scaleY = 0.5f
            tv.animate().scaleX(1f).scaleY(1f).setDuration(300).start()

            if (ghost.isLast) {
                tv.postDelayed({
                    val targetX = binding.displayContainer.x + 40f
                    val targetY = binding.displayContainer.y + binding.tvDisplay.y + (binding.tvDisplay.height / 4f)

                    val startX = tv.x
                    val startY = tv.y

                    val animator = android.animation.ValueAnimator.ofFloat(0f, 1f)
                    animator.duration = 6000
                    animator.interpolator = AccelerateDecelerateInterpolator()

                    animator.addUpdateListener { animation ->
                        val p = animation.animatedValue as Float
                        val bx = startX + p * (targetX - startX)
                        val by = startY + p * (targetY - startY)

                        val dx = targetX - startX
                        val dy = targetY - startY
                        val dist = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()

                        if (dist > 0) {
                            val nx = -dy / dist
                            val ny = dx / dist
                            val wobble = Math.sin(p * Math.PI * 4).toFloat() * 40f
                            tv.x = bx + wobble * nx
                            tv.y = by + wobble * ny
                        } else {
                            tv.x = bx
                            tv.y = by
                        }
                        tv.rotation = 45f + Math.sin(p * Math.PI * 10).toFloat() * 5f
                    }

                    animator.addListener(object : android.animation.AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: android.animation.Animator) {
                            viewModel.finalizeSlidingReveal()
                            binding.ghostContainer.removeView(tv)
                            hapticManager.magicVibrate()
                            soundManager.playMagicSwoosh()
                        }
                    })

                    animator.start()
                }, 2000L)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        sensorCoordinator.pause()
        volumeVoiceSession?.let { appContainer.voiceCaptureManager.release(it) }
        volumeVoiceSession = null

        // Hand off to background service if wallpaper/voice magic is enabled.
        // Stop it if a completely different magic (Contact Force, Digit Flip, etc.) is active.
        if (VoiceFeaturePolicy.shouldRun(settingsManager)) {
            if (MicPermission.has(this)) {
                StealthVoiceService.start(this)
            }
        } else {
            StealthVoiceService.stop(this)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorCoordinator.destroy()
        volumeVoiceSession?.let { appContainer.voiceCaptureManager.release(it) }
        volumeVoiceSession = null
        // Always stop the foreground mic service when the app is fully destroyed.
        StealthVoiceService.stop(this)
    }

    private fun triggerWallpaperForce(overrideContent: String? = null) {
        if (!settingsManager.isWallpaperMagicEnabled) return
        val isCardMode = settingsManager.wallpaperContentType == "CARD"
        val currentDisplay = binding.tvDisplay.text.toString().trim()

        lifecycleScope.launch {
            val target = when (settingsManager.wallpaperMagicTarget) {
                1 -> WallpaperMagicManager.Target.HOME
                2 -> WallpaperMagicManager.Target.LOCK
                else -> WallpaperMagicManager.Target.BOTH
            }

            val success = if (isCardMode) {
                val targetCard = overrideContent ?: settingsManager.forcedWallpaperCard
                WallpaperMagicManager.applyCardWallpaper(
                    this@MainActivity,
                    targetCard,
                    settingsManager.wallpaperStyleIndex,
                    target
                )
            } else {
                val targetNumber = overrideContent
                    ?: if (currentDisplay.isNotEmpty() && currentDisplay != "0" && currentDisplay != "Error") {
                        currentDisplay
                    } else {
                        settingsManager.forcedWallpaperNumber
                    }
                WallpaperMagicManager.applyNumberWallpaper(
                    this@MainActivity,
                    targetNumber,
                    settingsManager.wallpaperStyleIndex,
                    target
                )
            }

            if (success) {
                hapticManager.magicVibrate()
                soundManager.playMagicSwoosh()
            }
        }
    }

    private var volumeVoiceSession: VoiceCaptureManager.SessionHandle? = null

    /**
     * Hardware volume-down inside the calculator toggles one hands-free capture session.
     *
     * The session is owned by [VoiceCaptureManager], never by a listener built here, so the
     * locked-screen service and the Wallpaper & Voice Force dialog cannot end up with a second
     * `SpeechRecognizer` alive at the same time. That collision is what used to surface as
     * "Speech Recognition and Synthesis from Google cannot record".
     */
    private fun toggleVolumeVoiceCapture() {
        val manager = appContainer.voiceCaptureManager

        volumeVoiceSession?.let { session ->
            android.util.Log.d("MainActivity", "Volume Down pressed to STOP voice recording")
            volumeVoiceSession = null
            manager.release(session)
            hapticManager.micStopFeedback() // Single firm buzz: STOPPED
            return
        }

        if (!MicPermission.ensure(this)) {
            return
        }

        // Our own beep-muting surfaces as a volume drop a few hundred ms later. Reading it as the
        // magician's trigger would stack a session on top of the one that just ducked.
        if (manager.isInsideSelfDuckWindow()) {
            android.util.Log.d("MainActivity", "Volume drop ignored — caused by our own beep mute")
            return
        }

        android.util.Log.d("MainActivity", "Volume Down pressed to START voice recording session")

        // 1. Immediate tactile confirmation: Magician's finger instantly feels double-pulse (CHALU HUA)
        hapticManager.micStartFeedback()

        val activeMode = settingsManager.wallpaperContentType // "CARD" or "NUMBER"
        volumeVoiceSession = manager.requestSession(
            caller = VoiceCaptureManager.Caller.ACTIVITY_VOLUME,
            mode = activeMode,
            onStateChanged = { state ->
                android.util.Log.d("MainActivity", "Vol Voice State ($activeMode): $state")
            },
            onCardCaptured = { card ->
                runOnUiThread {
                    volumeVoiceSession = null
                    android.util.Log.d("MainActivity", "🃏 Secret Vol Capture Card: ${card.englishName}")

                    soundManager.playMagicSwoosh()
                    settingsManager.forcedWallpaperCard = card.code
                    val cardDisplay = "${card.rank}${card.symbol}"
                    viewModel.forceDisplay(cardDisplay)
                    binding.tvDisplay.text = cardDisplay

                    triggerWallpaperForce(card.code)
                }
            },
            onNumberCaptured = { spokenNumber ->
                runOnUiThread {
                    volumeVoiceSession = null
                    android.util.Log.d("MainActivity", "🎯 Secret Vol Capture: $spokenNumber")

                    soundManager.playMagicSwoosh()
                    settingsManager.forcedWallpaperNumber = spokenNumber
                    viewModel.forceDisplay(spokenNumber)
                    binding.tvDisplay.text = spokenNumber

                    triggerWallpaperForce(spokenNumber)
                }
            },
            onEnded = { reason ->
                // The manager already stopped the recognizer and buzzed for terminal failures, so
                // this only clears the handle and explains a failure the magician can act on.
                // Routine ERROR_NO_MATCH no longer lands here: silence keeps the session open until
                // the timeout, which is how the trick is supposed to work.
                runOnUiThread {
                    volumeVoiceSession = null
                    android.util.Log.d("MainActivity", "Vol voice session ended: $reason")
                    when (reason) {
                        VoiceCaptureManager.EndReason.PERMISSION_DENIED ->
                            MagicToast.show(this, "🎤 Microphone permission is off — grant it in Settings.")
                        VoiceCaptureManager.EndReason.MIC_BUSY ->
                            MagicToast.show(this, "🎤 Mic busy — close the other app recording and tap volume down again.")
                        VoiceCaptureManager.EndReason.REJECTED ->
                            android.util.Log.d("MainActivity", "Session refused — a higher-priority capture is live")
                        else -> Unit
                    }
                }
            }
        )
    }

    private fun executeP2PCommand(command: String) {
        if (settingsManager.appMode == 2) return
        if (InteractiveCoachManager.isTrainingActive) return

        runOnUiThread {
            when {
                command == "TRG:FLIP" -> {
                    viewModel.onHardwareTrigger()
                    hapticManager.magicVibrate()
                    soundManager.playMagicSwoosh()
                }
                command == "TRG:EXPL" -> {
                    if (!physicsEngine.isButtonsExploded) {
                        explodeButtons()
                    } else {
                        restoreButtons()
                    }
                }
                command.startsWith("FRC:TIM:") -> {
                    val time = command.substringAfter("FRC:TIM:")
                    settingsManager.forcedGalleryTime = time
                    MagicToast.show(this, "Remote Prediction Set: $time")
                }
                command.startsWith("FRC:PHN:") -> {
                    val phone = command.substringAfter("FRC:PHN:")
                    settingsManager.forcedPhoneNumber = phone
                    MagicToast.show(this, "Remote Phone Force Set: $phone")
                }
            }
        }
    }

    // ── Unlock Hint Banner ────────────────────────────────────────────────────

    /**
     * Shows the persistent "Type 741 = to unlock" hint banner.
     * Called when a new user skips the basic tour.
     */
    fun showUnlockHintBanner() {
        val banner = binding.layoutUnlockHint
        banner.visibility = View.VISIBLE
        // Animate slide-down from top
        banner.translationY = -banner.height.toFloat()
        banner.animate().translationY(0f).setDuration(350).start()

        // Dismiss button
        val btnDismiss = banner.findViewById<android.widget.TextView>(R.id.btnDismissUnlockHint)
        btnDismiss?.setOnClickListener {
            hideUnlockHintBanner()
        }
    }

    /**
     * Hides the unlock hint banner.
     * Called automatically when the user successfully does 741=.
     */
    fun hideUnlockHintBanner() {
        val banner = binding.layoutUnlockHint
        if (banner.visibility != View.VISIBLE) return
        InteractiveCoachManager.clearSkippedFlag()
        banner.animate().translationY(-banner.height.toFloat()).setDuration(300).withEndAction {
            banner.visibility = View.GONE
        }.start()
    }
}