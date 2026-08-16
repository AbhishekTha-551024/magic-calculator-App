package com.magic.calculator

import android.Manifest
import android.animation.ObjectAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
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
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.magic.calculator.audio.SoundManager
import com.magic.calculator.databinding.ActivityMainBinding
import com.magic.calculator.haptic.HapticManager
import com.magic.calculator.logic.AuthManager
import com.magic.calculator.logic.MagicEngine
import com.magic.calculator.logic.PremiumManager
import com.magic.calculator.logic.SettingsManager
import com.magic.calculator.logic.SubscriptionManager
import com.magic.calculator.p2p.P2PLinkManager
import com.magic.calculator.sensor.SensorCoordinator
import com.magic.calculator.ui.CallLogAdapter
import com.magic.calculator.ui.CallLogEntry
import com.magic.calculator.ui.ContactAdapter
import com.magic.calculator.ui.ContactEntry
import com.magic.calculator.ui.physics.PhysicsExplosionEngine
import com.magic.calculator.viewmodel.MagicViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
 */
class MainActivity : AppCompatActivity(), SensorCoordinator.SensorEventListenerCallback {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MagicViewModel by viewModels()

    private lateinit var settingsManager: SettingsManager
    private lateinit var soundManager: SoundManager
    private lateinit var hapticManager: HapticManager
    private lateinit var sensorCoordinator: SensorCoordinator
    private lateinit var physicsEngine: PhysicsExplosionEngine

    private val magicEngine = MagicEngine()
    private val PERMISSION_REQUEST_CODE = 100
    private var dialInput = ""
    private var isMissedOnly = false
    private var lastDisplayLength = 0
    private var isRestoringOrTransitioning = false

    // Lucky Magic Logic
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

        if (settingsManager.appMode == 3) {
            if (!PremiumManager.isUserPremium) {
                settingsManager.appMode = 0
            } else {
                startActivity(Intent(this, com.magic.calculator.ui.GalleryActivity::class.java))
                finish()
                return
            }
        }

        if (settingsManager.appMode == 4) {
            if (!PremiumManager.isUserPremium) {
                settingsManager.appMode = 0
            } else {
                val serviceIntent = Intent(this, com.magic.calculator.ui.moilock.MoiLockService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
            }
        }

        setupObservers()
        setupClickListeners()
        setupDialerListeners()

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
        settingsManager = SettingsManager(this)
        soundManager = SoundManager(this)
        hapticManager = HapticManager(this)
        physicsEngine = PhysicsExplosionEngine(soundManager, hapticManager)
        sensorCoordinator = SensorCoordinator(this, settingsManager, lifecycleScope, this)

        setupP2PListener()
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
        p2p.listener = object : P2PLinkManager.P2PEventListener {
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
        triggerHardwareMagic()
    }

    override fun onStepRevealTrigger(triggerType: String) {
        viewModel.revealStepWithTrigger(triggerType)
    }

    override fun onLuckyTrigger(value: Int) {
        setLuckyDisplay(value)
    }

    override fun onExplosionTrigger(triggerType: String) {
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

        val activeMagic = settingsManager.activeMagic
        val isPremiumTrick = activeMagic in listOf("TOXIC_TIME", "GHOST_TYPING", "STEP_REVEAL", "CONTACT_FORCE", "TOXIC_GALLERY")
        val isSubscribed = SubscriptionManager.isPremiumUser(this) || PremiumManager.isUserPremium
        if (isPremiumTrick && !isSubscribed) {
            settingsManager.activeMagic = "NONE"
            startActivity(Intent(this, PremiumActivity::class.java))
            Toast.makeText(this, "Premium features disabled. Please purchase premium.", Toast.LENGTH_LONG).show()
            return
        }

        if (settingsManager.isDialerThemeActive) {
            if (activeMagic == "DIGIT_FLIP") {
                if (dialInput == settingsManager.customFlipTrigger && !sensorCoordinator.flipAlreadyPerformed) {
                    sensorCoordinator.flipAlreadyPerformed = true
                    performDialerMagic(MagicEngine.TrickType.DIGIT_FLIP)
                }
            } else if (activeMagic == "PHONE_NUMBER_FORCE") {
                performDialerMagic(MagicEngine.TrickType.PHONE_NUMBER_FORCE)
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

    private fun setupDialerListeners() {
        val dialButtons = listOf(
            binding.layoutDialer.dial0 to "0", binding.layoutDialer.dial1 to "1",
            binding.layoutDialer.dial2 to "2", binding.layoutDialer.dial3 to "3",
            binding.layoutDialer.dial4 to "4", binding.layoutDialer.dial5 to "5",
            binding.layoutDialer.dial6 to "6", binding.layoutDialer.dial7 to "7",
            binding.layoutDialer.dial8 to "8", binding.layoutDialer.dial9 to "9",
            binding.layoutDialer.dialStar to "*", binding.layoutDialer.dialHash to "#"
        )

        dialButtons.forEach { (btn, value) ->
            btn.setOnClickListener {
                soundManager.playButtonTick()
                hapticManager.buttonTick()
                if (sensorCoordinator.isLuckyActive && dialInput == "0") dialInput = ""
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
                    if (dialInput.isEmpty() && sensorCoordinator.isLuckyActive) "0" else dialInput
                soundManager.playButtonTick()
                hapticManager.buttonTick()
            }
        }
        binding.layoutDialer.btnDialBackspace.setOnLongClickListener {
            if (sensorCoordinator.isLuckyActive) { exitLuckyMode(); true } else false
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
                    1 -> { settingsManager.isDialerThemeDark = false; applyDialerTheme() }
                    2 -> { settingsManager.isDialerThemeDark = true; applyDialerTheme() }
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
        sensorCoordinator.isLuckyActive = false
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
        sensorCoordinator.isLuckyActive = true
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

        binding.layoutDialer.dialerRoot.setBackgroundColor(bgCol)
        binding.layoutDialer.dialerHeader.setBackgroundColor(bgCol)
        binding.layoutDialer.bottomNav.setBackgroundColor(bgCol)
        binding.layoutDialer.tvMainTitle.setTextColor(primaryColor)

        binding.layoutDialer.ivSelect.setColorFilter(primaryColor)
        binding.layoutDialer.ivMore.setColorFilter(primaryColor)

        updateSubTabUI()

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
        lifecycleScope.launch(Dispatchers.IO) {
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

            if (list.isEmpty()) {
                list.add(CallLogEntry("Mom",    "+91XXXXXXXXXX", "Incoming", "20:39"))
                list.add(CallLogEntry("Friend", "+91XXXXXXXXXX", "Missed",   "19:51"))
                list.add(CallLogEntry("Boss",   "+91XXXXXXXXXX", "Incoming", "18:12"))
                list.add(CallLogEntry("Sister", "+91XXXXXXXXXX", "Incoming", "14:32"))
                list.add(CallLogEntry("Uncle",  "+91XXXXXXXXXX", "Missed",   "11:20"))
            }

            withContext(Dispatchers.Main) {
                binding.layoutDialer.rvCallLog.layoutManager = LinearLayoutManager(this@MainActivity)
                binding.layoutDialer.rvCallLog.adapter = CallLogAdapter(list) { entry ->
                    showContactDetailScreen(entry.name, entry.number)
                }
            }
        }
    }

    private fun loadContacts() {
        lifecycleScope.launch(Dispatchers.IO) {
            val list = mutableListOf<ContactEntry>()
            val injectedList = listOf(
                ContactEntry("Mom",    "+91XXXXXXXXXX", "Mobile Telephony"),
                ContactEntry("Friend", "+91XXXXXXXXXX", "Mobile Telephony"),
                ContactEntry("Boss",   "+91XXXXXXXXXX", "Mobile Telephony"),
                ContactEntry("Sister", "+91XXXXXXXXXX", "Mobile Telephony"),
                ContactEntry("Uncle",  "+91XXXXXXXXXX", "Mobile Telephony")
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
                        val number = it.getString(numIdx) ?: "+91XXXXXXXXXX"
                        if (name.isNotEmpty()) {
                            list.add(ContactEntry(name, number, "Mobile Telephony"))
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val existingNames = list.map { it.name.lowercase().trim() }.toSet()
            injectedList.forEach {
                if (!existingNames.contains(it.name.lowercase().trim())) {
                    list.add(it)
                }
            }
            list.sortBy { it.name.lowercase() }

            withContext(Dispatchers.Main) {
                binding.layoutDialer.rvContacts.layoutManager = LinearLayoutManager(this@MainActivity)
                binding.layoutDialer.rvContacts.adapter = ContactAdapter(list) { contact ->
                    showContactDetailScreen(contact.name, contact.number)
                }
            }
        }
    }

    private fun showContactDetailScreen(name: String, phone: String) {
        if (settingsManager.isContactForceEnabled) {
            if (!PremiumManager.isUserPremium) {
                settingsManager.isContactForceEnabled = false
            } else {
                settingsManager.forcedContactName = if (name.isEmpty()) phone else name
                settingsManager.forcedContactPhone = phone
                settingsManager.activeMagic = "CONTACT_FORCE"
            }
        }

        runOnUiThread {
            binding.layoutDialer.tvDetailsName.text = if (name.isEmpty()) "Unknown" else name
            binding.layoutDialer.tvDetailsPhone.text = phone

            val cities = listOf("Mumbai", "Delhi", "Maharashtra", "Kolkata", "Gujarat", "Karnataka", "Punjab")
            val carriers = listOf("Jio Mobile", "Airtel cellular", "Vodafone Idea", "BSNL Telephony")
            val city = cities[abs(name.hashCode()) % cities.size]
            val carrier = carriers[abs(phone.hashCode()) % carriers.size]
            binding.layoutDialer.tvDetailsCarrier.text = "$city, $carrier"

            binding.layoutDialer.cvDetailsAvatar.setCardBackgroundColor(Color.parseColor("#3B82F6"))
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
                if (physicsEngine.isButtonsExploded) return@setOnClickListener
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
            if (sensorCoordinator.isLuckyActive) {
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
            if (sensorCoordinator.isLuckyActive) {
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
        if (sensorCoordinator.isLuckyActive) exitLuckyMode()
        sensorCoordinator.resetFlipState()
        sensorCoordinator.flipAlreadyPerformed = false
        isRestoringOrTransitioning = false
        hapticManager.magicVibrate()
    }

    private fun operatorClick(op: String) {
        soundManager.playButtonTick()
        hapticManager.buttonTick()
        viewModel.onOperatorClick(op)
    }

    private fun applySettings() {
        soundManager.setVolume(settingsManager.soundVolume)
        hapticManager.intensityScale = settingsManager.hapticIntensity / 100f
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (sensorCoordinator.isLuckyActive) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP)   { setLuckyDisplay(3); return true }
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) { setLuckyDisplay(4); return true }
        }
        val activeTrigger = settingsManager.activeSensorTrigger
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP   && activeTrigger == "VOL_UP")   { triggerHardwareMagic(); return true }
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && activeTrigger == "VOL_DOWN") { triggerHardwareMagic(); return true }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (sensorCoordinator.isLuckyActive && (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) return true
        return super.onKeyUp(keyCode, event)
    }

    override fun onResume() {
        super.onResume()
        isRestoringOrTransitioning = false
        applySettings()
        applyTheme()
        applySubscriptionState()
        sensorCoordinator.resume()
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
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorCoordinator.destroy()
    }

    private fun executeP2PCommand(command: String) {
        if (settingsManager.appMode == 2) return

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