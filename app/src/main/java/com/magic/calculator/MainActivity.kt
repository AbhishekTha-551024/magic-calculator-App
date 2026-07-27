package com.magic.calculator

import kotlinx.coroutines.*
import androidx.lifecycle.lifecycleScope
import com.magic.calculator.logic.AuthManager
import android.Manifest
import android.animation.ObjectAnimator
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
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.BounceInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import android.widget.Toast
import android.widget.FrameLayout
import android.graphics.Typeface
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.magic.calculator.audio.SoundManager
import com.magic.calculator.databinding.ActivityMainBinding
import com.magic.calculator.haptic.HapticManager
import com.magic.calculator.logic.MagicEngine
import com.magic.calculator.logic.SettingsManager
import com.magic.calculator.logic.PremiumManager
import com.magic.calculator.sensor.ShakeDetector
import com.magic.calculator.ui.CallLogAdapter
import com.magic.calculator.ui.CallLogEntry
import com.magic.calculator.ui.ContactAdapter
import com.magic.calculator.ui.ContactEntry
import com.magic.calculator.viewmodel.MagicViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MagicViewModel by viewModels()

    private lateinit var settingsManager: SettingsManager
    
    private lateinit var soundManager: SoundManager
    private lateinit var hapticManager: HapticManager
    private lateinit var sensorManager: SensorManager
    private lateinit var shakeDetector: ShakeDetector

    private var accel: Sensor? = null
    private var prox: Sensor? = null
    private var light: Sensor? = null
    private var mag: Sensor? = null
    private var gyro: Sensor? = null

    private val magicEngine = MagicEngine()
    private val PERMISSION_REQUEST_CODE = 100
    private var dialInput = ""
    private var isMissedOnly = false
    private var lastDisplayLength = 0
    private var activeGhostView: TextView? = null
    private var isButtonsExploded = false
    private var lastExplodeTime = 0L
    private var isRestoringOrTransitioning = false

    // Live Physics Simulation (Tilt Gravity)
    private val physicsHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var physicsRunnable: Runnable? = null
    private var gravityX = 0f
    private var gravityY = 0f
    private val velX = mutableMapOf<Int, Float>()
    private val velY = mutableMapOf<Int, Float>()

    // Lucky Magic Logic
    private var isLuckyActive = false
    private var lastLuckyActionTime = 0L
    private val LUCKY_COOLDOWN = 300L

    // ─────────────────────────────────────────────────────────────────────────
    // DIGITAL FLIP — Completely rewritten gyroscope rotation tracking
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * One-shot guard: becomes TRUE the moment the flip fires.
     * Only resets when the display changes AWAY from BOTH the trigger
     * number AND the flipped result — i.e. the user has cleared and
     * started fresh. This prevents re-triggering while the flipped
     * number is still on screen.
     */
    private var flipAlreadyPerformed = false

    // Gyro integration state (nanosecond timestamps for precision)
    private var lastGyroTimeNano = 0L          // timestamp of previous gyro event

    // Per-flip accumulator: how many radians have we rotated in THIS flip?
    private var currentFlipAngle = 0f          // resets to 0 after each completed 180°

    // How many complete 180° flips have we counted in the current sequence?
    private var completedFlipCount = 0

    // Timestamp of the last completed flip (used for sequence timeout & debounce)
    private var lastFlipCompletedMs = 0L

    // Timestamp of the last gyro event that exceeded the speed threshold
    // (used to detect "rotation stopped" so we can reset currentFlipAngle)
    private var lastActiveGyroMs = 0L

    companion object {
        // Must rotate faster than this (rad/s) to count — ignores casual tilts
        private const val FLIP_SPEED_THRESHOLD = 2.5f   // rad/s  (~143°/s)

        // Angle threshold per "one rotation" in radians.
        // Set to ~332° (5.8 rad) so that ONE full back-and-forth wrist rotation
        // (camera goes up → down → up, ≈360° total abs movement) counts as exactly
        // ONE flip — not two. Previously 2.8f (160°) caused one wrist motion to
        // count as 2 flips, making the trick fire after only 2 rotations instead of 4.
        private const val HALF_ROTATION_RAD = 5.8f      // ~332° ≈ one full wrist rotation

        // Max gap between flips before the sequence resets (ms)
        private const val FLIP_SEQUENCE_TIMEOUT_MS = 8000L  // 8s — more time for 4 rotations

        // Minimum time between two consecutive flip completions (debounce, ms)
        private const val FLIP_DEBOUNCE_MS = 600L       // 600ms — prevents double-counting fast spins

        // If no fast rotation for this long, the in-progress flip angle resets (ms)
        private const val FLIP_STALL_TIMEOUT_MS = 800L

        // Number of full wrist rotations required to trigger the magic
        private const val REQUIRED_FLIPS = 3
    }
    // ─────────────────────────────────────────────────────────────────────────

    // Audio Trigger
    private var audioRecord: AudioRecord? = null
    private var audioJob: kotlinx.coroutines.Job? = null
    private var isAudioMonitoring = false
    private var lastRotationSide = -1
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

        if (settingsManager.appMode == 3) {
            if (!PremiumManager.isUserPremium) {
                settingsManager.appMode = 0 // Reset to normal calculator
            } else {
                startActivity(android.content.Intent(this, com.magic.calculator.ui.GalleryActivity::class.java))
                finish()
                return
            }
        }
        
        if (settingsManager.appMode == 4) {
            if (!PremiumManager.isUserPremium) {
                settingsManager.appMode = 0 // Reset to normal calculator
            } else {
                val serviceIntent = android.content.Intent(this, com.magic.calculator.ui.moilock.MoiLockService::class.java)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
            }
            // Allow the user to use the normal calculator UI, the lock screen will only trigger when the screen turns off
        }

        setupObservers()
        setupClickListeners()
        setupDialerListeners()

        checkPermissions()
        runIntroSequence()
        applyTheme()
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

    private fun updateSystemBarsForTheme() {
        val isDialer = settingsManager.isDialerThemeActive
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = isDialer
        controller.isAppearanceLightNavigationBars = isDialer
    }

    private fun initSystems() {
        settingsManager = SettingsManager(this)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        prox = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        light = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        mag = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        soundManager = SoundManager(this)
        hapticManager = HapticManager(this)

        shakeDetector = ShakeDetector { count ->
            if (count >= 1) {
                if (viewModel.isWaitingForShake && count >= 3) {
                    viewModel.jumpLastDigitToEnd()
                    hapticManager.magicVibrate()
                    soundManager.playMagicSwoosh()
                } else if (isLuckyActive) {
                    setLuckyDisplay(6)
                } else if (settingsManager.activeSensorTrigger == "SHAKE") {
                    triggerHardwareMagic()
                }
            }
        }
        shakeDetector.threshold = 7.0f

        // Initialize P2P Link manager and set up MainActivity listener for remote commands/mirror
        setupP2PListener()
    }

    private fun setupP2PListener() {
        val p2p = com.magic.calculator.p2p.P2PLinkManager.getInstance()
        // Auto-initialize P2P when user is logged in (registers Magic ID silently)
        if (com.magic.calculator.logic.AuthManager.currentUid != null) {
            if (p2p.myMagicId.isEmpty()) {
                p2p.initialize { /* Magic ID registered silently */ }
            } else {
                p2p.startListeningForRequests()
            }
        }
        // Attach listener to handle remote commands and mirror typing from partner
        p2p.listener = object : com.magic.calculator.p2p.P2PLinkManager.P2PEventListener {
            override fun onIncomingRequest(fromMagicId: String, requestDocId: String) {
                runOnUiThread {
                    android.app.AlertDialog.Builder(this@MainActivity)
                        .setTitle("Incoming Magic Link")
                        .setMessage("Connection request from $fromMagicId.\nAccept to sync displays?")
                        .setPositiveButton("Accept") { _, _ ->
                            p2p.acceptRequest(requestDocId, fromMagicId)
                            Toast.makeText(this@MainActivity, "Connected to $fromMagicId", Toast.LENGTH_SHORT).show()
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
                // Mirror partner's typing onto our calculator display
                runOnUiThread {
                    if (settingsManager.appMode != 2) { // not Real Calc mode
                        viewModel.setRemoteDisplay(display, formula)
                    }
                }
            }
            override fun onRemoteCommand(command: String) {
                executeP2PCommand(command)
            }
            override fun onError(message: String) { /* silent */ }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SENSOR HANDLING
    // ─────────────────────────────────────────────────────────────────────────

    private fun handleGravityEvent(event: SensorEvent) {
        // Pitch-based sliding removed as per user request for automatic slow slide
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            shakeDetector.onSensorChanged(event)
        }

        // Lucky Magic overrides everything else
        if (settingsManager.activeMagic == "LUCKY" && isLuckyActive) {
            when (event.sensor.type) {
                Sensor.TYPE_PROXIMITY  -> if (event.values[0] < 5.0f) setLuckyDisplay(1)
                Sensor.TYPE_LIGHT      -> if (event.values[0] < 5)    setLuckyDisplay(5)
                Sensor.TYPE_ACCELEROMETER -> {
                    shakeDetector.onSensorChanged(event)
                    if (event.values[2] < -8.5) setLuckyDisplay(2)
                }
            }
            return
        }

        val activeTrigger = settingsManager.activeSensorTrigger
        val activeMagic   = settingsManager.activeMagic

        when (event.sensor.type) {

            Sensor.TYPE_PROXIMITY -> {
                if (event.values[0] < 5.0f) {
                    // Check for Button Explosion Trigger
                    if (settingsManager.isExplosionEnabled && settingsManager.explosionTrigger == "PROXIMITY") {
                        if (isRestoringOrTransitioning) return
                        if (!isButtonsExploded) {
                            explodeButtons()
                        } else {
                            triggerGalleryTransition()
                        }
                        return
                    }

                    when {
                        activeMagic == "STEP_REVEAL" -> viewModel.revealStepWithTrigger("PROXIMITY")
                        activeTrigger == "PROXIMITY"  -> triggerHardwareMagic()
                    }
                }
            }

            Sensor.TYPE_LIGHT -> {
                if (event.values[0] < 5) {
                    when {
                        activeMagic == "STEP_REVEAL" -> viewModel.revealStepWithTrigger("LIGHT")
                        activeTrigger == "LIGHT"      -> triggerHardwareMagic()
                    }
                }
            }

            Sensor.TYPE_MAGNETIC_FIELD -> {
                val strength = sqrt(
                    (event.values[0] * event.values[0] +
                     event.values[1] * event.values[1] +
                     event.values[2] * event.values[2]).toDouble()
                )
                if (strength > 120) {
                    // Check for Button Explosion Trigger
                    if (settingsManager.isExplosionEnabled && settingsManager.explosionTrigger == "MAGNETIC") {
                        if (isRestoringOrTransitioning) return
                        if (!isButtonsExploded) {
                            explodeButtons()
                        } else {
                            triggerGalleryTransition()
                        }
                        return
                    }

                    when {
                        activeMagic == "STEP_REVEAL" -> viewModel.revealStepWithTrigger("MAGNETIC")
                        activeTrigger == "MAGNETIC"   -> triggerHardwareMagic()
                    }
                }
            }

            Sensor.TYPE_ACCELEROMETER -> {
                handleGravityEvent(event)
                if (isButtonsExploded) { gravityX = event.values[0]; gravityY = event.values[1] }
                shakeDetector.onSensorChanged(event)

                if (abs(event.values[0]) > 7) {
                    when {
                        activeMagic == "STEP_REVEAL" -> viewModel.revealStepWithTrigger("TILT")
                        activeTrigger == "TILT"       -> triggerHardwareMagic()
                    }
                }

                val z = event.values[2]
                lastRotationSide = if (z > 7.0) 0 else if (z < -7.0) 1 else -1

                if (z < -8.5) {
                    when {
                        activeMagic == "STEP_REVEAL" -> viewModel.revealStepWithTrigger("FACE_DOWN")
                        activeTrigger == "FACE_DOWN"  -> triggerHardwareMagic()
                    }
                }
            }

            Sensor.TYPE_GYROSCOPE -> {
                handleGyroscopeEvent(event, activeTrigger, activeMagic)
            }
        }
    }

    /**
     * DIGITAL FLIP — Gyroscope handler (completely rewritten).
     *
     * Logic:
     *  1. Only process when activeTrigger == "ROTATION_3" OR activeMagic == "DIGIT_FLIP"
     *     with trigger set to ROTATION_3. This prevents any other trigger mode
     *     from accidentally firing the flip.
     *  2. Uses high-precision nanosecond dt integration.
     *  3. Requires rotation speed > FLIP_SPEED_THRESHOLD to count — slow/casual
     *     tilts are completely ignored.
     *  4. Accumulates angle for the dominant axis (pitch OR roll, whichever is faster).
     *     Mixing axes is disabled — you must flip cleanly on one axis.
     *  5. Each time accumulator crosses HALF_ROTATION_RAD (~160°), one flip is
     *     counted and the accumulator resets.
     *  6. If rotation stalls for FLIP_STALL_TIMEOUT_MS, the in-progress flip
     *     angle resets (prevents slow drift accumulating over time).
     *  7. After REQUIRED_FLIPS (4) complete flips within FLIP_SEQUENCE_TIMEOUT_MS,
     *     triggerHardwareMagic() fires ONCE (guarded by flipAlreadyPerformed).
     *  8. Spin trick (Z-axis) still works for GYRO trigger as before.
     */
    private fun handleGyroscopeEvent(
        event: SensorEvent,
        activeTrigger: String,
        activeMagic: String
    ) {
        val rotX = event.values[0]   // pitch (tilt forward/back — NOT used for ROTATION_3)
        val rotY = event.values[1]   // roll  (tilt left/right  — NOT used for ROTATION_3)
        val rotZ = event.values[2]   // yaw   (steering-wheel spin, screen always facing user — USED for ROTATION_3)

        // ── Spin trick (fast Z-axis) — only for GYRO trigger ─────────────────
        // ROTATION_3 also uses Z-axis but slowly (counts 3×180°), so GYRO
        // needs a higher threshold (6 rad/s) so they don't conflict.
        if (activeTrigger == "GYRO" && abs(rotZ) > 6f) {
            triggerHardwareMagic()
            return
        }

        // ── Digital Flip 4-rotation logic ────────────────────────────────────
        // ONLY run this block when the trigger is explicitly ROTATION_3.
        // This is the KEY fix: no other trigger can cause this to fire.
        if (activeTrigger != "ROTATION_3") return

        val currentTimeNano = System.nanoTime()
        val currentTimeMs   = System.currentTimeMillis()

        if (lastGyroTimeNano == 0L) {
            lastGyroTimeNano = currentTimeNano
            return
        }

        val dt = (currentTimeNano - lastGyroTimeNano) / 1_000_000_000f  // seconds
        lastGyroTimeNano = currentTimeNano

        // Guard against huge dt spikes (e.g. after onPause/onResume)
        if (dt <= 0f || dt > 0.5f) return

        // ── Sequence timeout: reset everything if idle too long ───────────────
        if (currentTimeMs - lastFlipCompletedMs > FLIP_SEQUENCE_TIMEOUT_MS && completedFlipCount > 0) {
            resetFlipState()
        }

        // ── Stall detection: reset in-progress rotation if spin stopped ───────
        // We ONLY use the Z-axis (rotZ) — the steering-wheel / clock-face spin.
        // This means: hold the phone upright, screen facing you, and spin it
        // left-right (top goes left, bottom goes right) — like a 6 turning into a 9.
        // Face-down flips (rotX / rotY) are INTENTIONALLY IGNORED here.
        val spinSpeed = abs(rotZ)
        if (spinSpeed > FLIP_SPEED_THRESHOLD) {
            lastActiveGyroMs = currentTimeMs
        } else {
            if (currentTimeMs - lastActiveGyroMs > FLIP_STALL_TIMEOUT_MS && currentFlipAngle > 0f) {
                // Rotation stalled mid-spin — discard partial rotation
                currentFlipAngle = 0f
            }
            return  // Not spinning fast enough on Z-axis — ignore this sample
        }

        // ── Integrate Z-axis rotation only ───────────────────────────────────
        // Each 180° of Z-axis spin = one "flip" of the 6→9 style rotation.
        currentFlipAngle += spinSpeed * dt

        // ── Check if one 180° flip is complete ───────────────────────────────
        if (currentFlipAngle >= HALF_ROTATION_RAD) {
            val timeSinceLastFlip = currentTimeMs - lastFlipCompletedMs

            // Debounce: don't count two flips from one fast rotation
            if (timeSinceLastFlip > FLIP_DEBOUNCE_MS || completedFlipCount == 0) {
                completedFlipCount++
                lastFlipCompletedMs = currentTimeMs
                currentFlipAngle = 0f   // Reset for next flip

                // Clear vibration feedback so the magician can feel each rotation completion
                hapticManager.shakeFeedback(3) // 100ms for a solid feel

                // ── All 3 flips done — fire the magic! ───────────────────────
                if (completedFlipCount >= REQUIRED_FLIPS) {
                    // Small delay so the 3rd vibration is felt before the magic swoosh
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        triggerHardwareMagic()
                        resetFlipState()
                    }, 150)
                }
            } else {
                // Too fast — just reset the angle but don't count the flip
                currentFlipAngle = 0f
            }
        }
    }

    /** Resets ALL Digital Flip tracking state cleanly. */
    private fun resetFlipState() {
        completedFlipCount = 0
        currentFlipAngle   = 0f
        lastFlipCompletedMs = 0L
        lastActiveGyroMs    = 0L
        lastGyroTimeNano    = 0L
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // ─────────────────────────────────────────────────────────────────────────
    // TRIGGER HARDWARE MAGIC
    // ─────────────────────────────────────────────────────────────────────────

    private fun triggerHardwareMagic() {
        if (settingsManager.appMode == 2) return // No magic in Real Calc mode

        // MASTER GATE: If no sensor trigger is selected, NO physical motion
        // (flip, face-down, shake, gyro, tilt, etc.) should fire any magic.
        // The magician must explicitly select a trigger in the secret menu.
        if (settingsManager.activeSensorTrigger == "NONE") return

        val activeMagic = settingsManager.activeMagic
        val isPremiumTrick = activeMagic in listOf("TOXIC_TIME", "GHOST_TYPING", "STEP_REVEAL", "CONTACT_FORCE", "TOXIC_GALLERY")
        if (isPremiumTrick && !PremiumManager.isUserPremium) {
            settingsManager.activeMagic = "NONE"
            startActivity(Intent(this, PremiumActivity::class.java))
            Toast.makeText(this, "Premium features disabled. Please purchase premium.", Toast.LENGTH_LONG).show()
            return
        }

        if (settingsManager.isDialerThemeActive) {
            if (activeMagic == "DIGIT_FLIP") {
                // One-shot: only flip if exact trigger number is on screen
                if (dialInput == settingsManager.customFlipTrigger && !flipAlreadyPerformed) {
                    flipAlreadyPerformed = true
                    performDialerMagic(MagicEngine.TrickType.DIGIT_FLIP)
                }
            } else if (activeMagic == "PHONE_NUMBER_FORCE") {
                performDialerMagic(MagicEngine.TrickType.PHONE_NUMBER_FORCE)
            }
        } else {
            val currentDisplay = viewModel.displayText.value ?: ""
            if (activeMagic == "DIGIT_FLIP") {
                // One-shot: only flip if exact trigger number is on screen
                if (currentDisplay == settingsManager.customFlipTrigger && !flipAlreadyPerformed) {
                    flipAlreadyPerformed = true
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

    // ─────────────────────────────────────────────────────────────────────────
    // OBSERVERS — flipAlreadyPerformed reset logic FIXED
    // ─────────────────────────────────────────────────────────────────────────

    private fun setupObservers() {
        viewModel.displayText.observe(this) { text ->
            binding.tvDisplay.text = text
            lastDisplayLength = text.length

            // P2P Mirror Typing: broadcast display changes to connected partner
            val p2p = com.magic.calculator.p2p.P2PLinkManager.getInstance()
            if (p2p.isConnected && p2p.isMirrorEnabled && !viewModel.isReceivingRemote) {
                val formula = viewModel.formulaText.value ?: ""
                p2p.syncDisplay(text, formula)
            }

            // Time Snatcher Interception Logic
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

            // FIX: Only reset the flip guard when the display is completely
            // cleared back to "0" (or empty). Previously it reset any time the
            // display changed — including right AFTER the flip fired, which
            // allowed a second trigger on the very same rotation sequence.
            val trigger = settingsManager.customFlipTrigger
            val result  = settingsManager.customFlipResult
            if (text != trigger && text != result && (text == "0" || text.isEmpty())) {
                flipAlreadyPerformed = false
                resetFlipState()  // Also reset rotation counters when user clears
            }
        }
        viewModel.formulaText.observe(this) { binding.tvFormula.text = it }
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
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Everything below is UNCHANGED from your original MainActivity
    // ─────────────────────────────────────────────────────────────────────────

    private fun setupDialerListeners() {
        val dialButtons = listOf(
            binding.layoutDialer.dial0 to "0",
            binding.layoutDialer.dial1 to "1",
            binding.layoutDialer.dial2 to "2",
            binding.layoutDialer.dial3 to "3",
            binding.layoutDialer.dial4 to "4",
            binding.layoutDialer.dial5 to "5",
            binding.layoutDialer.dial6 to "6",
            binding.layoutDialer.dial7 to "7",
            binding.layoutDialer.dial8 to "8",
            binding.layoutDialer.dial9 to "9",
            binding.layoutDialer.dialStar to "*",
            binding.layoutDialer.dialHash to "#"
        )

        dialButtons.forEach { (btn, value) ->
            btn.setOnClickListener {
                soundManager.playButtonTick()
                hapticManager.buttonTick()
                if (isLuckyActive && dialInput == "0") dialInput = ""
                dialInput += value
                binding.layoutDialer.tvDialNumber.text = dialInput
            }
            btn.setOnLongClickListener {
                if (settingsManager.activeSensorTrigger == "FINGERPRINT") {
                    triggerHardwareMagic(); true
                } else false
            }
        }
        binding.layoutDialer.btnDialBackspace.setOnClickListener {
            if (dialInput.isNotEmpty()) {
                dialInput = dialInput.dropLast(1)
                binding.layoutDialer.tvDialNumber.text =
                    if (dialInput.isEmpty() && isLuckyActive) "0" else dialInput
                soundManager.playButtonTick()
                hapticManager.buttonTick()
            }
        }
        binding.layoutDialer.btnDialBackspace.setOnLongClickListener {
            if (isLuckyActive) { exitLuckyMode(); true } else false
        }
        binding.layoutDialer.btnCall.setOnClickListener { handleCallAction() }
        binding.layoutDialer.bottomNav.setOnItemSelectedListener { item ->
            switchDialerTab(item.itemId); true
        }
        binding.layoutDialer.tabAll.setOnClickListener {
            isMissedOnly = false; updateSubTabUI(); loadCallLogs()
        }
        binding.layoutDialer.tabMissed.setOnClickListener {
            isMissedOnly = true; updateSubTabUI(); loadCallLogs()
        }
        binding.layoutDialer.fabAction.setOnClickListener {
            binding.layoutDialer.dialPadOverlay.visibility = View.VISIBLE
        }
        binding.layoutDialer.vDimmer.setOnClickListener {
            binding.layoutDialer.dialPadOverlay.visibility = View.GONE
        }
        binding.layoutDialer.btnDetailsBack.setOnClickListener {
            binding.layoutDialer.layoutContactDetails.visibility = View.GONE
        }
        binding.layoutDialer.btnDetailsCall.setOnClickListener {
            val phone = binding.layoutDialer.tvDetailsPhone.text.toString()
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
            startActivity(intent)
            binding.layoutDialer.layoutContactDetails.visibility = View.GONE
        }
        binding.layoutDialer.btnDetailsVideo.setOnClickListener {
            openGalleryDirectly()
        }
        binding.layoutDialer.cvDetailsAvatar.setOnLongClickListener {
            openGalleryDirectly()
            true
        }
        binding.layoutDialer.ivMore.setOnClickListener { view ->
            val popup = androidx.appcompat.widget.PopupMenu(this, view)
            popup.menu.add(0, 1, 0, "☀️ Day Theme (Light)")
            popup.menu.add(0, 2, 1, "🌙 Night Theme (Dark)")
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    1 -> {
                        settingsManager.isDialerThemeDark = false
                        applyDialerTheme()
                    }
                    2 -> {
                        settingsManager.isDialerThemeDark = true
                        applyDialerTheme()
                    }
                }
                true
            }
            popup.show()
        }
    }

    private fun openGalleryDirectly() {
        PremiumManager.enforceGatekeeper(
            onUnlocked = {
                if (settingsManager.isContactForceEnabled) {
                    settingsManager.activeMagic = "CONTACT_FORCE"
                }
                val intent = Intent(this, com.magic.calculator.ui.GalleryActivity::class.java)
                startActivity(intent)
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                finish()
            },
            onLocked = {
                startActivity(Intent(this, PremiumActivity::class.java))
                Toast.makeText(this, "Premium required for Gallery & Contact Force!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun exitLuckyMode() {
        isLuckyActive = false
        dialInput = ""
        runOnUiThread {
            binding.layoutDialer.tvDialNumber.text = ""
            binding.tvDisplay.text = "0"
            viewModel.onClearClick()
            hapticManager.magicVibrate()
            soundManager.playButtonTick()
        }
    }

    private fun handleCallAction() {
        when (dialInput) {
            "741" -> {
                startActivity(Intent(this, SecretActivity::class.java))
                dialInput = ""
                binding.layoutDialer.tvDialNumber.text = ""
                binding.layoutDialer.dialPadOverlay.visibility = View.GONE
            }
            settingsManager.customFlipTrigger -> performDialerMagic(MagicEngine.TrickType.DIGIT_FLIP)
            "777" -> {
                if (settingsManager.activeMagic == "LUCKY") startLuckyMagic()
                else startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$dialInput")))
            }
            else -> {
                if (dialInput.isNotEmpty())
                    startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$dialInput")))
            }
        }
    }

    private fun startLuckyMagic() {
        isLuckyActive = true
        dialInput = "0"
        runOnUiThread {
            binding.layoutDialer.tvDialNumber.text = "0"
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
            dialInput = valStr
            binding.layoutDialer.tvDialNumber.text = valStr
            binding.tvDisplay.text = valStr
            viewModel.forceDisplay(valStr)
            hapticManager.buttonTick()
            soundManager.playMagicSwoosh()
        }
    }

    private fun performDialerMagic(type: MagicEngine.TrickType) {
        val result = magicEngine.performMagic(
            input = dialInput,
            type = type,
            forcedPhone = settingsManager.forcedPhoneNumber,
            forcedLucky = settingsManager.forcedLuckyNumber,
            flipResult  = settingsManager.customFlipResult
        )
        val animator = ObjectAnimator.ofFloat(binding.layoutDialer.tvDialNumber, "rotationX", 0f, 360f)
        animator.duration = 600
        animator.interpolator = AccelerateDecelerateInterpolator()
        animator.addUpdateListener {
            if (animator.animatedFraction > 0.5f) {
                binding.layoutDialer.tvDialNumber.text = result.displayValue
                dialInput = result.displayValue
            }
        }
        animator.start()
        hapticManager.magicVibrate()
        soundManager.playMagicSwoosh()
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

        // 1. Root and Headers backgrounds
        binding.layoutDialer.dialerRoot.setBackgroundColor(bgCol)
        binding.layoutDialer.dialerHeader.setBackgroundColor(bgCol)
        binding.layoutDialer.bottomNav.setBackgroundColor(bgCol)
        binding.layoutDialer.tvMainTitle.setTextColor(primaryColor)

        // 2. Toolbar icon tinting
        binding.layoutDialer.ivSelect.setColorFilter(primaryColor)
        binding.layoutDialer.ivMore.setColorFilter(primaryColor)

        // 3. Sub Tabs & Call Logs List
        updateSubTabUI()

        // 4. Bottom Nav Icon/Text Colors
        val tintList = android.content.res.ColorStateList.valueOf(secondaryColor)
        binding.layoutDialer.bottomNav.itemIconTintList = tintList
        binding.layoutDialer.bottomNav.itemTextColor = tintList

        // 5. Dial Pad Overlay background & text
        binding.layoutDialer.dialPadOverlay.findViewById<View>(R.id.vDimmer)?.setBackgroundColor(
            if (isDark) Color.parseColor("#44000000") else Color.parseColor("#05000000")
        )
        val dialPadContainer = binding.layoutDialer.dialPadOverlay.getChildAt(1) as? ConstraintLayout
        dialPadContainer?.setBackgroundColor(bgCol)
        binding.layoutDialer.tvDialNumber.setTextColor(primaryColor)

        // Iterate through all 12 dial buttons to set their Text/Letters colors
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

        // 6. Contact Details sheet theme
        binding.layoutDialer.layoutContactDetails.setBackgroundColor(bgCol)
        binding.layoutDialer.detailsToolbar.setBackgroundColor(bgCol)
        binding.layoutDialer.btnDetailsBack.setColorFilter(primaryColor)
        binding.layoutDialer.btnDetailsEdit.setColorFilter(primaryColor)
        binding.layoutDialer.layoutContactDetails.findViewById<TextView>(R.id.tvDetailsName)?.setTextColor(primaryColor)
        binding.layoutDialer.tvDetailsPhone.setTextColor(primaryColor)
        binding.layoutDialer.tvDetailsCarrier.setTextColor(secondaryColor)

        // Labels
        binding.layoutDialer.tvDetailsMessageLabel.setTextColor(secondaryColor)
        binding.layoutDialer.tvDetailsCallLabel.setTextColor(secondaryColor)
        binding.layoutDialer.tvDetailsVideoLabel.setTextColor(secondaryColor)

        // Divider in details
        binding.layoutDialer.vDetailsDivider.setBackgroundColor(
            if (isDark) Color.parseColor("#333333") else Color.parseColor("#EEEEEE")
        )

        // Call logs header inside details
        binding.layoutDialer.tvDetailsCallLogsTitle.setTextColor(primaryColor)
        binding.layoutDialer.tvDetailsMoreLink.setTextColor(secondaryColor)

        // Call log items inside details
        binding.layoutDialer.tvLogType1.setTextColor(primaryColor)
        binding.layoutDialer.tvLogType2.setTextColor(primaryColor)
        binding.layoutDialer.tvLogDuration1.setTextColor(secondaryColor)
        binding.layoutDialer.tvLogDuration2.setTextColor(secondaryColor)
        binding.layoutDialer.tvLogTime1.setTextColor(secondaryColor)
        binding.layoutDialer.tvLogTime2.setTextColor(secondaryColor)

        // 7. Status bar & Navigation bar theme setup
        window.statusBarColor = bgCol
        window.navigationBarColor = bgCol
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = !isDark
        controller.isAppearanceLightNavigationBars = !isDark

        // 8. Refresh RecyclerView Adapters to bind updated text/secondary colors
        binding.layoutDialer.rvCallLog.adapter?.notifyDataSetChanged()
        binding.layoutDialer.rvContacts.adapter?.notifyDataSetChanged()
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

    private fun startAudioMonitoring() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED) return
        isAudioMonitoring = true
        audioJob?.cancel()
        audioJob = lifecycleScope.launch(Dispatchers.Default) {
            try {
                val bufferSize = AudioRecord.getMinBufferSize(
                    44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                val record = AudioRecord(
                    MediaRecorder.AudioSource.MIC, 44100,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize)
                audioRecord = record
                val buffer = ShortArray(bufferSize)
                record.startRecording()
                while (isAudioMonitoring && isActive) {
                    val read = record.read(buffer, 0, bufferSize)
                    if (read > 0) {
                        var max = 0
                        for (i in 0 until read) {
                            val value = abs(buffer[i].toInt())
                            if (value > max) max = value
                        }
                        if (settingsManager.activeSensorTrigger == "CLAP" && max > 18000) {
                            withContext(kotlinx.coroutines.Dispatchers.Main) {
                                triggerHardwareMagic()
                            }
                            kotlinx.coroutines.delay(1500)
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

    private fun checkPermissions() {
        val perms = arrayOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_CONTACTS)
        if (perms.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
            ActivityCompat.requestPermissions(this, perms, PERMISSION_REQUEST_CODE)
        } else {
            loadCallLogs()
            loadContacts()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            loadCallLogs()
            loadContacts()
        }
    }

    private fun loadCallLogs() {
        val list = mutableListOf<CallLogEntry>()
        try {
            val cursor = contentResolver.query(
                CallLog.Calls.CONTENT_URI, null, null, null, CallLog.Calls.DATE + " DESC")
            val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            cursor?.use {
                val numIdx  = it.getColumnIndex(CallLog.Calls.NUMBER)
                val nameIdx = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val typeIdx = it.getColumnIndex(CallLog.Calls.TYPE)
                val dateIdx = it.getColumnIndex(CallLog.Calls.DATE)
                var count = 0
                while (it.moveToNext() && count < 20) {
                    val type    = it.getInt(typeIdx)
                    val typeStr = if (type == CallLog.Calls.MISSED_TYPE) "Missed" else "Incoming"
                    if (isMissedOnly && type != CallLog.Calls.MISSED_TYPE) continue
                    list.add(CallLogEntry(
                        it.getString(nameIdx) ?: "", it.getString(numIdx) ?: "",
                        typeStr, dateFormat.format(Date(it.getLong(dateIdx)))))
                    count++
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Fallback robust mock recent calls if log is empty
        if (list.isEmpty()) {
            list.add(CallLogEntry("💞💖Papa🙏🙏", "+918959777541", "Incoming", "20:39"))
            list.add(CallLogEntry("Mahendra Singh", "9265827893", "Missed", "19:51"))
            list.add(CallLogEntry("Shanu Parauh", "8815114272", "Incoming", "18:12"))
            list.add(CallLogEntry("Sudha", "9876543210", "Incoming", "14:32"))
            list.add(CallLogEntry("Uma Singh Mam", "9827163544", "Missed", "11:20"))
        }

        binding.layoutDialer.rvCallLog.layoutManager = LinearLayoutManager(this)
        binding.layoutDialer.rvCallLog.adapter = CallLogAdapter(list) { entry ->
            showContactDetailScreen(entry.name, entry.number)
        }
    }

    private fun loadContacts() {
        val list = mutableListOf<ContactEntry>()
        val injectedList = listOf(
            ContactEntry("💞💖Papa🙏🙏", "+918959777541", "Chhattisgarh Mobile Telephony"),
            ContactEntry("Mahendra Singh", "9265827893", "Maharashtra Mobile Telephony"),
            ContactEntry("Shanu Parauh", "8815114272", "Madhya Pradesh Mobile Telephony"),
            ContactEntry("Sudha", "9876543210", "Delhi Mobile Telephony"),
            ContactEntry("Uma Singh Mam", "9827163544", "Uttar Pradesh Mobile Telephony")
        )

        try {
            val cursor = contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, null, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC")
            cursor?.use {
                val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (it.moveToNext()) {
                    val name = it.getString(nameIdx) ?: ""
                    val number = it.getString(numIdx) ?: "+918959777541"
                    if (name.isNotEmpty()) {
                        list.add(ContactEntry(name, number, "Mobile Telephony"))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Merge device contacts with injected ones to prevent duplicates
        val existingNames = list.map { it.name.lowercase().trim() }.toSet()
        injectedList.forEach {
            if (!existingNames.contains(it.name.lowercase().trim())) {
                list.add(it)
            }
        }
        list.sortBy { it.name.lowercase() }

        binding.layoutDialer.rvContacts.layoutManager = LinearLayoutManager(this)
        binding.layoutDialer.rvContacts.adapter = ContactAdapter(list) { contact ->
            showContactDetailScreen(contact.name, contact.number)
        }
    }

    /**
     * Fully populates and launches the contact detail fullscreen sheet overlay layoutContactDetails.
     * Intercepts and triggers the magical Contact Force variables on the fly!
     */
    private fun showContactDetailScreen(name: String, phone: String) {
        val settingsManager = com.magic.calculator.logic.SettingsManager(this)
        if (settingsManager.isContactForceEnabled) {
            if (!PremiumManager.isUserPremium) {
                settingsManager.isContactForceEnabled = false
            } else {
                // Save these clicked details in settings to dynamically force badging overlays!
                settingsManager.forcedContactName = if (name.isEmpty()) phone else name
                settingsManager.forcedContactPhone = phone
                settingsManager.activeMagic = "CONTACT_FORCE" // Set active magic to CONTACT_FORCE
            }
        }

        runOnUiThread {
            binding.layoutDialer.tvDetailsName.text = if (name.isEmpty()) "Unknown" else name
            binding.layoutDialer.tvDetailsPhone.text = phone
            
            // Randomize visual carrier text to look completely authentic
            val cities = listOf("Mumbai", "Delhi", "Maharashtra", "Kolkata", "Gujarat", "Karnataka", "Punjab")
            val carriers = listOf("Jio Mobile", "Airtel cellular", "Vodafone Idea", "BSNL Telephony")
            val city = cities[Math.abs(name.hashCode()) % cities.size]
            val carrier = carriers[Math.abs(phone.hashCode()) % carriers.size]
            binding.layoutDialer.tvDetailsCarrier.text = "$city, $carrier"

            // Set Avatar letter
            binding.layoutDialer.cvDetailsAvatar.setCardBackgroundColor(Color.parseColor("#3B82F6"))

            // Display overlay
            binding.layoutDialer.layoutContactDetails.visibility = View.VISIBLE
        }
    }

    private fun updateSubTabUI() {
        val isDark = settingsManager.isDialerThemeDark
        val primaryColor = if (isDark) Color.WHITE else Color.parseColor("#212121")
        val secondaryColor = if (isDark) Color.parseColor("#BBBBBB") else Color.parseColor("#757575")

        if (isMissedOnly) {
            binding.layoutDialer.tvTabAll.setTextColor(secondaryColor)
            binding.layoutDialer.indicatorAll.visibility = View.INVISIBLE
            binding.layoutDialer.tvTabMissed.setTextColor(primaryColor)
            binding.layoutDialer.indicatorMissed.visibility = View.VISIBLE
        } else {
            binding.layoutDialer.tvTabAll.setTextColor(primaryColor)
            binding.layoutDialer.indicatorAll.visibility = View.VISIBLE
            binding.layoutDialer.tvTabMissed.setTextColor(secondaryColor)
            binding.layoutDialer.indicatorMissed.visibility = View.INVISIBLE
        }
    }

    private fun switchDialerTab(itemId: Int) {
        binding.layoutDialer.rvCallLog.visibility  = View.GONE
        binding.layoutDialer.rvContacts.visibility = View.GONE
        binding.layoutDialer.dialerHeader.visibility = View.VISIBLE
        binding.layoutDialer.fabAction.visibility    = View.GONE
        binding.layoutDialer.fabAddContact.visibility = View.GONE
        when (itemId) {
            R.id.nav_dial -> {
                binding.layoutDialer.rvCallLog.visibility  = View.VISIBLE
                binding.layoutDialer.fabAction.visibility  = View.VISIBLE
                binding.layoutDialer.subTabs.visibility    = View.VISIBLE
                binding.layoutDialer.tvMainTitle.text      = "Dial"
                loadCallLogs()
            }
            R.id.nav_contacts -> {
                binding.layoutDialer.rvContacts.visibility    = View.VISIBLE
                binding.layoutDialer.fabAddContact.visibility = View.VISIBLE
                binding.layoutDialer.subTabs.visibility       = View.GONE
                binding.layoutDialer.tvMainTitle.text         = "Contacts"
                loadContacts()
            }
        }
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
                        // Check if it's a "Top" touch (upper part of the screen)
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

    private fun setupClickListeners() {
        setupSwipeToDelete()
        val buttons = listOf(
            binding.btn0 to "0", binding.btn1 to "1", binding.btn2 to "2",
            binding.btn3 to "3", binding.btn4 to "4", binding.btn5 to "5",
            binding.btn6 to "6", binding.btn7 to "7", binding.btn8 to "8",
            binding.btn9 to "9", binding.btnDot to "."
        )
        buttons.forEach { (btn, value) ->
            btn.setOnClickListener {
                if (isButtonsExploded) return@setOnClickListener
                soundManager.playButtonTick()
                hapticManager.buttonTick()
                viewModel.onNumberClick(value)
            }
        }
        binding.btnAdd.setOnClickListener { operatorClick("+") }
        binding.btnSub.setOnClickListener { operatorClick("-") }
        binding.btnMul.setOnClickListener { operatorClick("*") }
        binding.btnDiv.setOnClickListener { operatorClick("/") }
        binding.btnBackspace.setOnClickListener {
            viewModel.onBackspaceClick()
            if (isLuckyActive) {
                dialInput = "0"
                binding.layoutDialer.tvDialNumber.text = "0"
                binding.tvDisplay.text = "0"
                viewModel.forceDisplay("0")
            }
            soundManager.playButtonTick()
            hapticManager.buttonTick()
        }
        binding.btnBackspace.setOnLongClickListener { performPanicReset(); true }
        binding.btnEqual.setOnClickListener {
            if (viewModel.checkSecretUnlock()) {
                soundManager.playMagicSwoosh()
                hapticManager.magicVibrate()
                return@setOnClickListener
            }
            val calcDisplay = viewModel.displayText.value ?: ""
            val activeMagic = settingsManager.activeMagic
            if ((dialInput == "777" || calcDisplay == "777") && activeMagic == "LUCKY") {
                startLuckyMagic(); equalClickCount = 0
            } else if (activeMagic == "TOXIC_TIME") {
                val toxicValue = calculateToxicValue()
                viewModel.onHardwareTrigger(toxicValue); equalClickCount = 0
            } else {
                viewModel.onEqualClick()
                equalClickCount++
                if (equalClickCount >= 2 && activeMagic == "REAPPEARING_RESULT") {
                    hapticManager.buttonTick()
                }
            }
            soundManager.playButtonTick()
            hapticManager.buttonTick()
        }
        binding.btnEqual.setOnLongClickListener {
            if (settingsManager.activeMagic == "TOXIC_GALLERY") {
                hapticManager.magicVibrate()
                soundManager.playMagicSwoosh()
                settingsManager.appMode = 3
                val intent = Intent(this, com.magic.calculator.ui.GalleryActivity::class.java)
                startActivity(intent)
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                finish()
                true
            } else {
                false
            }
        }
        binding.btnPlusMinus.setOnClickListener { viewModel.onPlusMinusClick(); soundManager.playButtonTick(); hapticManager.buttonTick() }
        binding.btnPercent.setOnClickListener { viewModel.onPercentClick(); soundManager.playButtonTick(); hapticManager.buttonTick() }
        binding.btnC.setOnClickListener {
            viewModel.onClearClick()
            equalClickCount = 0
            isRestoringOrTransitioning = false
            if (isLuckyActive) {
                dialInput = "0"
                binding.layoutDialer.tvDialNumber.text = "0"
                binding.tvDisplay.text = "0"
                viewModel.forceDisplay("0")
            }
            soundManager.playButtonTick()
            hapticManager.buttonTick()
        }
        binding.btnC.setOnLongClickListener {
            performPanicReset()
            true
        }
        binding.layoutDialer.btnDialBackspace.setOnLongClickListener { performPanicReset(); true }
    }

    private fun performPanicReset() {
        settingsManager.activeMagic = "NONE"
        settingsManager.isContactForceEnabled = false
        settingsManager.isExplosionEnabled = false
        viewModel.wipeMagicMemory()
        dialInput = "0"
        equalClickCount = 0
        binding.layoutDialer.tvDialNumber.text = "0"
        if (isLuckyActive) exitLuckyMode()
        resetFlipState()
        flipAlreadyPerformed = false
        isRestoringOrTransitioning = false
        hapticManager.magicVibrate()
    }

    private fun operatorClick(op: String) {
        soundManager.playButtonTick()
        hapticManager.buttonTick()
        viewModel.onOperatorClick(op)
    }

    private fun applySettings() {
        shakeDetector.threshold = 7.0f
        soundManager.setVolume(settingsManager.soundVolume)
        hapticManager.intensityScale = settingsManager.hapticIntensity / 100f
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (isLuckyActive) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP)   { setLuckyDisplay(3); return true }
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) { setLuckyDisplay(4); return true }
        }
        val activeTrigger = settingsManager.activeSensorTrigger
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP   && activeTrigger == "VOL_UP")   { triggerHardwareMagic(); return true }
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && activeTrigger == "VOL_DOWN") { triggerHardwareMagic(); return true }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (isLuckyActive && (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) return true
        return super.onKeyUp(keyCode, event)
    }

    override fun onResume() {
        super.onResume()
        isRestoringOrTransitioning = false
        applySettings()
        applyTheme()
        // Reset gyro timestamp so we don't get a massive dt spike on first event
        lastGyroTimeNano = 0L
        accel?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        prox?.let  { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        light?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        mag?.let   { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        gyro?.let  { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        // NOTE: SENSOR_DELAY_GAME for gyro gives ~50Hz instead of UI's ~16Hz
        // — much better precision for rotation integration
        startAudioMonitoring()
    }

    private fun spawnDigitFromTop(digit: String) {
        val startX = binding.root.width / 2f
        spawnDigitWithAnimation(digit, startX, 0f, true)
    }

    private fun spawnDigitAtTap(digit: String, rawX: Float, rawY: Float) {
        val rootLocation = IntArray(2)
        binding.root.getLocationOnScreen(rootLocation)
        spawnDigitWithAnimation(digit, rawX - rootLocation[0], rawY - rootLocation[1], false)
    }

    private fun spawnDigitWithAnimation(digit: String, startX: Float, startY: Float, fromTop: Boolean) {
        val ghost = TextView(this).apply {
            text = digit
            setTextColor(Color.WHITE)
            textSize = 88f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            alpha    = 0f
            rotation = if (fromTop) 35f else -25f
            x = startX - 40f
            y = startY - 80f
        }
        binding.root.addView(ghost)
        ghost.animate().alpha(1f).setDuration(if (fromTop) 400L else 300L).withEndAction {
            ghost.postDelayed({
                val displayLoc = IntArray(2)
                binding.tvDisplay.getLocationOnScreen(displayLoc)
                ghost.animate()
                    .x(displayLoc[0] + binding.tvDisplay.width * 0.4f)
                    .y(displayLoc[1].toFloat())
                    .rotation(0f)
                    .setDuration(1200L)
                    .setInterpolator(DecelerateInterpolator(1.5f))
                    .withEndAction {
                        viewModel.finalizeStepReveal()
                        ghost.animate().alpha(0f).setDuration(100L)
                            .withEndAction { binding.root.removeView(ghost) }.start()
                        soundManager.playMagicSwoosh()
                        hapticManager.magicVibrate()
                    }.start()
            }, if (fromTop) 1000L else 2000L)
        }.start()
    }

    private fun explodeButtons() {
        if (isButtonsExploded) return
        isButtonsExploded = true
        lastExplodeTime = System.currentTimeMillis()
        soundManager.playMagicSwoosh()
        hapticManager.magicVibrate()
        val random = java.util.Random()
        binding.tvFormula.animate().alpha(0f).setDuration(200L).start()
        velX.clear(); velY.clear()
        val count = binding.buttonGrid.childCount
        for (i in 0..count) {
            velX[i] = (random.nextFloat() - 0.5f) * 6f
            velY[i] = (random.nextFloat() - 0.5f) * 6f
        }
        for (i in 0 until count) {
            val view = binding.buttonGrid.getChildAt(i)
            view.translationZ = 100f
            val randomX   = (random.nextInt(200) - 100).toFloat()
            val randomRot = (random.nextInt(70)  - 35).toFloat()
            val randomFall = 200f + random.nextInt(300).toFloat()
            view.animate().translationY(-200f).setDuration(250L)
                .setInterpolator(AccelerateDecelerateInterpolator()).withEndAction {
                    view.animate().translationY(randomFall).translationX(randomX)
                        .rotation(randomRot).alpha(1f)
                        .setInterpolator(BounceInterpolator()).setDuration(1000L)
                        .withEndAction { if (i == count - 1 && isButtonsExploded) startPhysicsLoop() }
                        .start()
                }.start()
        }
        binding.tvDisplay.animate().alpha(0f).translationY(400f).setDuration(400L).start()
    }

    private fun startPhysicsLoop() {
        stopPhysicsLoop()
        val screenW = binding.mainLayout.width.toFloat()
        val screenH = binding.mainLayout.height.toFloat()
        val count   = binding.buttonGrid.childCount
        val rng     = java.util.Random()
        val mass    = FloatArray(count + 1) { 0.5f + rng.nextFloat() * 1.0f }
        val damping = FloatArray(count + 1) { 0.84f + rng.nextFloat() * 0.12f }
        physicsRunnable = object : Runnable {
            override fun run() {
                if (!isButtonsExploded) return
                val gx = -gravityX; val gy = gravityY
                for (i in 0 until count) {
                    val view = binding.buttonGrid.getChildAt(i)
                    var vx = velX.getOrDefault(i, 0f); var vy = velY.getOrDefault(i, 0f)
                    val accel = 0.5f / mass[i]; val damp = damping[i]
                    vx += gx * accel; vy += gy * accel
                    vx += (rng.nextFloat() - 0.5f) * 0.5f; vy += (rng.nextFloat() - 0.5f) * 0.5f
                    vx *= damp; vy *= damp
                    var tx = view.translationX + vx; var ty = view.translationY + vy
                    val maxX = screenW * 0.42f; val maxY = screenH * 0.50f; val minY = -screenH * 0.30f
                    if (tx >  maxX) { tx =  maxX; vx = -vx * 0.35f }
                    if (tx < -maxX) { tx = -maxX; vx = -vx * 0.35f }
                    if (ty >  maxY) { ty =  maxY; vy = -vy * 0.35f }
                    if (ty <  minY) { ty =  minY; vy = -vy * 0.35f }
                    view.translationX = tx; view.translationY = ty
                    velX[i] = vx; velY[i] = vy
                }
                val di = count
                var vx = velX.getOrDefault(di, 0f); var vy = velY.getOrDefault(di, 0f)
                val accel = 0.5f / mass[di]
                vx += gx * accel + (rng.nextFloat() - 0.5f) * 0.3f
                vy += gy * accel + (rng.nextFloat() - 0.5f) * 0.3f
                vx *= damping[di]; vy *= damping[di]
                var tx = binding.tvDisplay.translationX + vx; var ty = binding.tvDisplay.translationY + vy
                if (tx >  screenW * 0.35f) { tx =  screenW * 0.35f; vx = -vx * 0.35f }
                if (tx < -screenW * 0.35f) { tx = -screenW * 0.35f; vx = -vx * 0.35f }
                if (ty >  screenH * 0.30f) { ty =  screenH * 0.30f; vy = -vy * 0.35f }
                if (ty < -screenH * 0.20f) { ty = -screenH * 0.20f; vy = -vy * 0.35f }
                binding.tvDisplay.translationX = tx; binding.tvDisplay.translationY = ty
                velX[di] = vx; velY[di] = vy
                physicsHandler.postDelayed(this, 16L)
            }
        }
        physicsHandler.post(physicsRunnable!!)
    }

    private fun startSlidingReveal(digit: String) {
        // Replaced by delayed slide in addGhostDigitToScreen
    }

    private fun addGhostDigitToScreen(ghost: MagicViewModel.GhostDigit) {
        val tv = TextView(this).apply {
            text = ghost.digit
            setTextColor(Color.WHITE)
            textSize = 96f // Exactly match the main display size
            alpha = 1.0f  // Fully solid
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            rotation = 45f // 45 degree tilt as requested
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        binding.ghostContainer.addView(tv)
        
        tv.post {
            tv.x = ghost.x - (tv.width / 2)
            tv.y = ghost.y - (tv.height / 2)
            
            // Initial pop-in
            tv.scaleX = 0.5f; tv.scaleY = 0.5f
            tv.animate().scaleX(1f).scaleY(1f).setDuration(300).start()

            // If it's the last digit, wait 2s then slide slowly to first place
            if (ghost.isLast) {
                tv.postDelayed({
                    val targetX = binding.displayContainer.x + 40f // Align with the left side of the display area
                    val targetY = binding.displayContainer.y + binding.tvDisplay.y + (binding.tvDisplay.height / 4f) // Align with the display text row
                    
                    val startX = tv.x
                    val startY = tv.y
                    
                    val animator = android.animation.ValueAnimator.ofFloat(0f, 1f)
                    animator.duration = 6000 // Very slow 6-second pull
                    animator.interpolator = AccelerateDecelerateInterpolator()
                    
                    animator.addUpdateListener { animation ->
                        val p = animation.animatedValue as Float
                        
                        // Linear interpolation for the base path
                        val bx = startX + p * (targetX - startX)
                        val by = startY + p * (targetY - startY)
                        
                        // Add a "ghostly wobble" (sine wave perpendicular to direction)
                        val dx = targetX - startX
                        val dy = targetY - startY
                        val dist = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                        
                        if (dist > 0) {
                            val nx = -dy / dist // normal vector X
                            val ny = dx / dist  // normal vector Y
                            val wobble = Math.sin(p * Math.PI * 4).toFloat() * 40f // 2 cycles of 40dp wobble
                            
                            tv.x = bx + wobble * nx
                            tv.y = by + wobble * ny
                        } else {
                            tv.x = bx
                            tv.y = by
                        }
                        
                        // Rotation: Keep it tilted at 45 degrees with a slight "struggle" oscillation
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
                }, 2000L) // Wait 2 seconds before starting the pull
            }
        }
    }

    private fun triggerGalleryTransition() {
        if (!isButtonsExploded) return
        if (isRestoringOrTransitioning) return
        if (System.currentTimeMillis() - lastExplodeTime < 1500L) return
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
                
                val intent = Intent(this, com.magic.calculator.ui.PhotoViewerActivity::class.java).apply {
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

    private fun stopPhysicsLoop() {
        physicsRunnable?.let { physicsHandler.removeCallbacks(it) }
        physicsRunnable = null
    }

    private fun restoreButtons() {
        viewModel.isStepRevealActive = false
        if (!isButtonsExploded) return
        isButtonsExploded = false
        stopPhysicsLoop()
        soundManager.playMagicSwoosh()
        hapticManager.magicVibrate()
        for (i in 0 until binding.buttonGrid.childCount) {
            val view = binding.buttonGrid.getChildAt(i)
            view.translationZ = 0f
            view.animate().translationY(0f).translationX(0f).rotation(0f).alpha(1f)
                .setDuration(800L).setInterpolator(DecelerateInterpolator(1.5f)).start()
        }
        binding.tvDisplay.animate().translationY(0f).translationX(0f).rotation(0f).alpha(1f)
            .setDuration(800L).setInterpolator(DecelerateInterpolator(1.5f)).start()
        binding.tvFormula.animate().translationY(0f).alpha(1f).setDuration(800L).start()
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        isAudioMonitoring = false
        audioJob?.cancel()
        audioJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        audioRecord = null
        lastGyroTimeNano = 0L  // Reset on pause so dt doesn't spike on resume
        // P2P: don't stop the link on pause — keep Firestore alive for background sync
        // The listener stays active so partner can still see display updates
    }

    override fun onDestroy() {
        super.onDestroy()
        // Do not stop P2P listeners here, as recreating MainActivity (e.g. from SecretActivity) will kill the active connection!
    }

    private fun executeP2PCommand(command: String) {
        if (settingsManager.appMode == 2) return // Real calc mode — no magic

        runOnUiThread {
            when {
                command == "TRG:FLIP" -> {
                    viewModel.onHardwareTrigger()
                    hapticManager.magicVibrate()
                    soundManager.playMagicSwoosh()
                }
                command == "TRG:EXPL" -> {
                    if (!isButtonsExploded) {
                        explodeButtons()
                    } else {
                        restoreButtons()
                    }
                }
                command.startsWith("FRC:TIM:") -> {
                    val time = command.substringAfter("FRC:TIM:")
                    settingsManager.forcedGalleryTime = time
                    Toast.makeText(this, "Remote Prediction Set: $time", Toast.LENGTH_SHORT).show()
                }
                command.startsWith("FRC:PHN:") -> {
                    val phone = command.substringAfter("FRC:PHN:")
                    settingsManager.forcedPhoneNumber = phone
                    Toast.makeText(this, "Remote Phone Force Set: $phone", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onBackPressed() {
        if (binding.layoutDialer.layoutContactDetails.visibility == View.VISIBLE) {
            binding.layoutDialer.layoutContactDetails.visibility = View.GONE
        } else if (binding.layoutDialer.dialPadOverlay.visibility == View.VISIBLE) {
            binding.layoutDialer.dialPadOverlay.visibility = View.GONE
        } else {
            super.onBackPressed()
        }
    }
}