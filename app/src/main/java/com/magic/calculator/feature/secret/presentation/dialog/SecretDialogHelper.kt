package com.magic.calculator.feature.secret.presentation.dialog

import android.app.Activity
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.magic.calculator.R
import com.magic.calculator.feature.auth.presentation.LoginActivity
import com.magic.calculator.feature.subscription.presentation.PremiumActivity
import com.magic.calculator.feature.subscription.presentation.PaymentHistoryActivity
import com.magic.calculator.feature.vault.presentation.GalleryActivity
import com.magic.calculator.feature.vault.presentation.PhotoViewerActivity
import com.magic.calculator.feature.applock.presentation.MoiLockActivity
import com.magic.calculator.feature.applock.presentation.service.MoiLockService
import com.magic.calculator.feature.applock.presentation.receiver.MoiDeviceAdminReceiver
import com.magic.calculator.core.audio.MicPermission
import com.magic.calculator.core.logic.AuthManager
import com.magic.calculator.core.logic.PinManager
import com.magic.calculator.core.logic.PremiumManager
import com.magic.calculator.core.logic.SettingsManager
import com.magic.calculator.core.logic.SubscriptionManager
import com.magic.calculator.feature.p2p.presentation.P2PLinkManager
import com.magic.calculator.feature.p2p.domain.model.P2PEventListener
import com.magic.calculator.core.ui.util.MagicToast
import android.Manifest
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.magic.calculator.di.appContainer
import com.magic.calculator.feature.voice_wallpaper.data.voice.VoiceCaptureManager
import com.magic.calculator.feature.voice_wallpaper.data.voice.VoiceCardExtractor
import com.magic.calculator.feature.voice_wallpaper.data.voice.VoiceNumberExtractor
import com.magic.calculator.feature.voice_wallpaper.data.wallpaper.WallpaperMagicManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * SecretDialogHelper
 *
 * Encapsulates modal dialogs for SecretActivity:
 * - MoiLock Setup
 * - Toxic Gallery Configuration
 * - Button Explosion Configuration
 * - P2P Online Magic Link
 * - Step Reveal Configuration
 * - Profile & Subscription Details
 * - Magic Performance Guides
 * - Contact Force Configuration
 */
object SecretDialogHelper {

    fun showMoiLockPanelDialog(
        activity: Activity,
        settingsManager: SettingsManager,
        wallpaperPickerLauncher: ActivityResultLauncher<String>,
        onActivateMagic: (String) -> Unit,
        onStatusChanged: () -> Unit
    ) {
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_moilock_settings)

        dialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0.85f)
        }

        val pinManager = PinManager(activity)

        val swMoiLockEnable = dialog.findViewById<SwitchMaterial>(R.id.swMoiLockEnable)
        val etSecret = dialog.findViewById<EditText>(R.id.dialogEtMoiLockSecret)
        val btnSaveSecret = dialog.findViewById<Button>(R.id.dialogBtnSaveMoiLockSecret)
        val etMaster = dialog.findViewById<EditText>(R.id.dialogEtMoiLockMasterPin)
        val btnSaveMaster = dialog.findViewById<Button>(R.id.dialogBtnSaveMoiLockMaster)
        val btnSelectWallpaper = dialog.findViewById<Button>(R.id.dialogBtnSelectMoiLockWallpaper)
        val btnBatterySaver = dialog.findViewById<Button>(R.id.dialogBtnBatterySaver)
        val btnLaunchMoiLock = dialog.findViewById<Button>(R.id.dialogBtnLaunchMoiLock)
        val btnClose = dialog.findViewById<ImageView>(R.id.btnCloseDialog)
        val tvPermStatus = dialog.findViewById<TextView>(R.id.tvPermissionsStatus)

        btnBatterySaver.setOnClickListener {
            try {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                activity.startActivity(intent)
            } catch (e: Exception) {
                // Ignore
            }
        }

        swMoiLockEnable.isChecked = (settingsManager.appMode == 4 && settingsManager.activeMagic == "MOILOCK")
        etSecret.setText(pinManager.secretDigit)
        etMaster.setText(pinManager.masterPin)

        val updatePermText = {
            val dpm = activity.getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
            val componentName = android.content.ComponentName(activity, MoiDeviceAdminReceiver::class.java)
            val adminActive = dpm.isAdminActive(componentName)
            val overlayActive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(activity)
            } else true

            var permText = "Permissions Status:\n"
            permText += if (adminActive) "✓ Device Admin Allowed\n" else "✗ Device Admin Missing\n"
            permText += if (overlayActive) "✓ Overlay Allowed" else "✗ Overlay Missing"
            tvPermStatus.text = permText
        }

        updatePermText()
        btnClose.setOnClickListener { dialog.dismiss() }

        swMoiLockEnable.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                onActivateMagic("MOILOCK")
                val dpm = activity.getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
                val componentName = android.content.ComponentName(activity, MoiDeviceAdminReceiver::class.java)
                if (!dpm.isAdminActive(componentName)) {
                    val intent = Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                        putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
                        putExtra(android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Required to simulate realistic lock screen.")
                    }
                    activity.startActivity(intent)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(activity)) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:${activity.packageName}")
                    )
                    activity.startActivity(intent)
                }

                val serviceIntent = Intent(activity, MoiLockService::class.java)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        activity.startForegroundService(serviceIntent)
                    } else {
                        activity.startService(serviceIntent)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                if (settingsManager.activeMagic == "MOILOCK" || settingsManager.appMode == 4) {
                    onActivateMagic("NONE")
                }
                val serviceIntent = Intent(activity, MoiLockService::class.java)
                try {
                    activity.stopService(serviceIntent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            onStatusChanged()
            updatePermText()
        }

        btnSaveSecret.setOnClickListener {
            val digit = etSecret.text.toString()
            if (digit.length == 1) {
                pinManager.secretDigit = digit
                pinManager.resetLock()
            }
        }

        btnSaveMaster.setOnClickListener {
            val pin = etMaster.text.toString()
            if (pin.length == 5) {
                pinManager.masterPin = pin
            }
        }

        btnSelectWallpaper.setOnClickListener {
            wallpaperPickerLauncher.launch("image/*")
        }

        btnLaunchMoiLock.setOnClickListener {
            val intent = Intent(activity, MoiLockActivity::class.java)
            activity.startActivity(intent)
        }

        dialog.show()
    }

    fun showToxicGalleryPanelDialog(
        activity: Activity,
        settingsManager: SettingsManager,
        onActivateMagic: (String) -> Unit
    ) {
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_toxic_gallery_settings)

        dialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0.85f)
        }

        val swToxicGalleryEnable = dialog.findViewById<SwitchMaterial>(R.id.swToxicGalleryEnable)
        val etForcedTime = dialog.findViewById<EditText>(R.id.dialogEtForcedTime)
        val btnSaveForcedTime = dialog.findViewById<Button>(R.id.dialogBtnSaveForcedTime)
        val btnLaunchGallery = dialog.findViewById<Button>(R.id.dialogBtnLaunchGallery)
        val btnClose = dialog.findViewById<ImageView>(R.id.btnCloseGalleryDialog)

        swToxicGalleryEnable.isChecked = (settingsManager.activeMagic == "TOXIC_GALLERY")
        etForcedTime.setText(settingsManager.forcedGalleryTime)

        btnClose.setOnClickListener { dialog.dismiss() }

        swToxicGalleryEnable.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) onActivateMagic("TOXIC_GALLERY")
            else if (settingsManager.activeMagic == "TOXIC_GALLERY") onActivateMagic("NONE")
        }

        btnSaveForcedTime.setOnClickListener {
            val forcedTime = etForcedTime.text.toString().trim()
            if (forcedTime.isNotEmpty()) {
                val formatted = forcedTime.replace(".", ":")
                settingsManager.forcedGalleryTime = formatted
            } else {
                settingsManager.forcedGalleryTime = "4:20"
            }
            dialog.dismiss()
        }

        btnLaunchGallery.setOnClickListener {
            dialog.dismiss()
            settingsManager.appMode = 3
            val intent = Intent(activity, GalleryActivity::class.java)
            activity.startActivity(intent)
        }

        dialog.show()
    }

    fun showExplosionPanelDialog(
        activity: Activity,
        settingsManager: SettingsManager,
        onActivateMagic: (String) -> Unit,
        onStatusChanged: () -> Unit
    ) {
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_explosion_settings)

        dialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0.85f)
        }

        val swExplosionEnable = dialog.findViewById<SwitchMaterial>(R.id.dialogSwExplosionEnable)
        val rgExplosionTrigger = dialog.findViewById<RadioGroup>(R.id.dialogRgExplosionTrigger)
        val rbExplosionProximity = dialog.findViewById<RadioButton>(R.id.dialogRbExplosionProximity)
        val rbExplosionMagnetic = dialog.findViewById<RadioButton>(R.id.dialogRbExplosionMagnetic)
        val btnClose = dialog.findViewById<ImageView>(R.id.btnCloseExplosionDialog)

        swExplosionEnable.isChecked = (settingsManager.isExplosionEnabled && settingsManager.activeMagic == "BUTTON_EXPLOSION")
        if (settingsManager.explosionTrigger == "PROXIMITY") {
            rbExplosionProximity.isChecked = true
        } else {
            rbExplosionMagnetic.isChecked = true
        }

        btnClose.setOnClickListener { dialog.dismiss() }

        swExplosionEnable.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                onActivateMagic("BUTTON_EXPLOSION")
            } else if (settingsManager.activeMagic == "BUTTON_EXPLOSION" || settingsManager.isExplosionEnabled) {
                onActivateMagic("NONE")
            }
            onStatusChanged()
        }

        rgExplosionTrigger.setOnCheckedChangeListener { _, checkedId ->
            val trigger = if (checkedId == R.id.dialogRbExplosionProximity) "PROXIMITY" else "MAGNETIC"
            settingsManager.explosionTrigger = trigger
        }

        dialog.show()
    }

    fun showP2PLinkPanelDialog(
        activity: Activity,
        settingsManager: SettingsManager,
        onActivateMagic: (String) -> Unit,
        onStatusChanged: () -> Unit
    ) {
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_p2p_link_settings)

        dialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0.87f)
        }

        val p2p = P2PLinkManager.getInstance()

        val swP2PEnable = dialog.findViewById<SwitchMaterial>(R.id.swP2PEnable)
        val layoutP2PContent = dialog.findViewById<LinearLayout>(R.id.layoutP2PContent)
        val tvMyMagicId = dialog.findViewById<TextView>(R.id.tvMyMagicId)
        val btnCopyMagicId = dialog.findViewById<ImageView>(R.id.btnCopyMagicId)
        val etPartnerMagicId = dialog.findViewById<EditText>(R.id.etPartnerMagicId)
        val btnSendRequest = dialog.findViewById<Button>(R.id.btnSendRequest)
        val tvP2PStatus = dialog.findViewById<TextView>(R.id.tvP2PStatus)
        val cardIncomingRequest = dialog.findViewById<View>(R.id.cardIncomingRequest)
        val tvIncomingRequestFrom = dialog.findViewById<TextView>(R.id.tvIncomingRequestFrom)
        val btnAcceptRequest = dialog.findViewById<Button>(R.id.btnAcceptRequest)
        val btnDeclineRequest = dialog.findViewById<Button>(R.id.btnDeclineRequest)
        val layoutMirrorModeRow = dialog.findViewById<View>(R.id.layoutMirrorModeRow)
        val swMirrorMode = dialog.findViewById<SwitchMaterial>(R.id.swMirrorMode)
        val btnDisconnect = dialog.findViewById<Button>(R.id.btnDisconnect)
        val cardRemoteTriggers = dialog.findViewById<View>(R.id.cardRemoteTriggers)
        val btnP2PTriggerFlip = dialog.findViewById<Button>(R.id.btnP2PTriggerFlip)
        val btnP2PTriggerExplosion = dialog.findViewById<Button>(R.id.btnP2PTriggerExplosion)
        val btnCloseDialog = dialog.findViewById<ImageView>(R.id.btnCloseP2PDialog)

        swP2PEnable.isChecked = (settingsManager.isP2PEnabled && settingsManager.activeMagic == "P2P_LINK")
        layoutP2PContent.visibility = if (settingsManager.isP2PEnabled) View.VISIBLE else View.GONE

        var pendingRequestDocId = ""
        var pendingRequestFromId = ""

        val updateStatus = { isConnected: Boolean, partner: String?, extra: String? ->
            if (isConnected && partner != null) {
                tvP2PStatus.text = "🟢 Connected to $partner"
                tvP2PStatus.setTextColor(Color.parseColor("#00FF66"))
                layoutMirrorModeRow.visibility = View.VISIBLE
                btnDisconnect.visibility = View.VISIBLE
                cardRemoteTriggers.visibility = View.VISIBLE
            } else if (extra != null) {
                tvP2PStatus.text = "🟡 $extra"
                tvP2PStatus.setTextColor(Color.parseColor("#FFCC00"))
                layoutMirrorModeRow.visibility = View.GONE
                btnDisconnect.visibility = View.GONE
                cardRemoteTriggers.visibility = View.GONE
            } else {
                tvP2PStatus.text = "🔴 Not Connected"
                tvP2PStatus.setTextColor(Color.parseColor("#FF4444"))
                layoutMirrorModeRow.visibility = View.GONE
                btnDisconnect.visibility = View.GONE
                cardRemoteTriggers.visibility = View.GONE
            }
        }

        swP2PEnable.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                settingsManager.isP2PEnabled = true
                layoutP2PContent.visibility = View.VISIBLE
                if (p2p.myMagicId.isEmpty() && AuthManager.currentUid != null) {
                    tvMyMagicId.text = "Setting up..."
                    p2p.initialize { magicId ->
                        activity.runOnUiThread { tvMyMagicId.text = magicId }
                        p2p.listenForAcceptedRequest { _, partnerMagicId ->
                            activity.runOnUiThread {
                                updateStatus(true, partnerMagicId, null)
                                onStatusChanged()
                                settingsManager.p2pPartnerMagicId = partnerMagicId
                            }
                        }
                    }
                } else if (p2p.myMagicId.isNotEmpty()) {
                    p2p.startListeningForRequests()
                }
            } else {
                settingsManager.isP2PEnabled = false
                layoutP2PContent.visibility = View.GONE
                p2p.disconnect()
                onStatusChanged()
            }
        }

        if (p2p.myMagicId.isNotEmpty()) {
            tvMyMagicId.text = p2p.myMagicId
        } else if (settingsManager.isP2PEnabled) {
            tvMyMagicId.text = "Setting up..."
            p2p.initialize { magicId ->
                activity.runOnUiThread { tvMyMagicId.text = magicId }
                p2p.listenForAcceptedRequest { _, partnerMagicId ->
                    activity.runOnUiThread {
                        updateStatus(true, partnerMagicId, null)
                        onStatusChanged()
                        settingsManager.isP2PEnabled = true
                        settingsManager.p2pPartnerMagicId = partnerMagicId
                    }
                }
            }
        }

        if (p2p.isConnected) {
            updateStatus(true, p2p.partnerMagicId, null)
            swMirrorMode.isChecked = p2p.isMirrorEnabled
        } else {
            updateStatus(false, null, null)
        }

        p2p.listener = object : P2PEventListener {
            override fun onIncomingRequest(fromMagicId: String, requestDocId: String) {
                activity.runOnUiThread {
                    pendingRequestDocId = requestDocId
                    pendingRequestFromId = fromMagicId
                    tvIncomingRequestFrom.text = "Connection request from $fromMagicId"
                    cardIncomingRequest.visibility = View.VISIBLE
                }
            }

            override fun onConnected(partnerMagicId: String, sessionId: String) {
                activity.runOnUiThread {
                    updateStatus(true, partnerMagicId, null)
                    onStatusChanged()
                    cardIncomingRequest.visibility = View.GONE
                    settingsManager.isP2PEnabled = true
                    settingsManager.p2pSessionId = sessionId
                    settingsManager.p2pPartnerMagicId = partnerMagicId
                }
            }

            override fun onDisconnected() {
                activity.runOnUiThread {
                    updateStatus(false, null, null)
                    onStatusChanged()
                    swMirrorMode.isChecked = false
                    settingsManager.isP2PEnabled = false
                    settingsManager.p2pSessionId = ""
                    settingsManager.p2pPartnerMagicId = ""
                }
            }

            override fun onRemoteDisplayUpdate(display: String, formula: String) {}
            override fun onRemoteCommand(command: String) {}
            override fun onError(message: String) {
                MagicToast.show(activity, message, isLong = true)
            }
        }

        btnCopyMagicId.setOnClickListener {
            val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Magic ID", p2p.myMagicId)
            clipboard.setPrimaryClip(clip)
            MagicToast.show(activity, "Magic ID '${p2p.myMagicId}' copied!")
        }

        btnSendRequest.setOnClickListener {
            val targetId = etPartnerMagicId.text.toString().trim()
            if (targetId.isEmpty()) {
                MagicToast.show(activity, "Enter your partner's Magic ID.")
                return@setOnClickListener
            }
            updateStatus(false, null, "Sending request...")
            p2p.sendRequest(targetId) { success, message ->
                activity.runOnUiThread {
                    MagicToast.show(activity, message, isLong = true)
                    if (success) {
                        updateStatus(false, null, "Waiting for acceptance...")
                        p2p.listenForAcceptedRequest { sessionId, partnerMagicId ->
                            activity.runOnUiThread {
                                updateStatus(true, partnerMagicId, null)
                                onStatusChanged()
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

        btnAcceptRequest.setOnClickListener {
            if (pendingRequestDocId.isNotEmpty()) {
                updateStatus(false, null, "Connecting...")
                p2p.acceptRequest(pendingRequestDocId, pendingRequestFromId)
                cardIncomingRequest.visibility = View.GONE
            }
        }

        btnDeclineRequest.setOnClickListener {
            if (pendingRequestDocId.isNotEmpty()) {
                p2p.declineRequest(pendingRequestDocId)
                cardIncomingRequest.visibility = View.GONE
                pendingRequestDocId = ""
                pendingRequestFromId = ""
                MagicToast.show(activity, "Request declined.")
            }
        }

        swMirrorMode.isChecked = p2p.isMirrorEnabled
        swMirrorMode.setOnCheckedChangeListener { _, isChecked ->
            p2p.setMirrorEnabled(isChecked)
            settingsManager.isP2PMirrorEnabled = isChecked
        }

        btnDisconnect.setOnClickListener { p2p.disconnect() }

        btnP2PTriggerFlip.setOnClickListener {
            if (p2p.isConnected) {
                p2p.sendCommand("TRG:FLIP")
                MagicToast.show(activity, "Flip trigger sent to partner!")
            } else {
                MagicToast.show(activity, "Not connected to any partner.")
            }
        }

        btnP2PTriggerExplosion.setOnClickListener {
            if (p2p.isConnected) {
                p2p.sendCommand("TRG:EXPL")
                MagicToast.show(activity, "Explosion trigger sent to partner!")
            } else {
                MagicToast.show(activity, "Not connected to any partner.")
            }
        }

        btnCloseDialog.setOnClickListener {
            p2p.listener = null
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun getTriggerDisplayName(trigger: String): String {
        return when (trigger.uppercase()) {
            "TOUCH" -> "👆 Screen Touch"
            "PROXIMITY" -> "👋 Proximity Wave"
            "FACE_DOWN" -> "📱 Face Down (Flip)"
            "LIGHT" -> "🌗 Light / Shadow"
            "MAGNETIC" -> "🧲 Magnetic / PK Ring"
            "TILT" -> "📐 Tilt / Motion"
            "VOL_DOWN" -> "🔉 Volume Down"
            "VOL_UP" -> "🔊 Volume Up"
            "ANY" -> "🎲 Any Sensor Trigger"
            else -> "👆 Screen Touch"
        }
    }

    private fun getTriggerSubtitle(trigger: String, stepNumber: Int): String {
        return when (trigger.uppercase()) {
            "TOUCH" -> "Step $stepNumber • Screen Tap"
            "PROXIMITY" -> "Step $stepNumber • Wave hand over camera"
            "FACE_DOWN" -> "Step $stepNumber • Flip phone face-down"
            "LIGHT" -> "Step $stepNumber • Palm shadow over light sensor"
            "MAGNETIC" -> "Step $stepNumber • PK Ring / Magnet near sensor"
            "TILT" -> "Step $stepNumber • Tilt phone up/down"
            "VOL_DOWN" -> "Step $stepNumber • Stealth click Volume Down"
            "VOL_UP" -> "Step $stepNumber • Stealth click Volume Up"
            "ANY" -> "Step $stepNumber • Any gesture or touch"
            else -> "Step $stepNumber • Screen Tap"
        }
    }

    private fun showTriggerPickerDialog(
        activity: Activity,
        stepNumber: Int,
        onTriggerSelected: (String) -> Unit
    ) {
        val pickerDialog = Dialog(activity)
        pickerDialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        pickerDialog.setContentView(R.layout.dialog_step_trigger_picker)

        pickerDialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0.85f)
        }

        pickerDialog.findViewById<TextView>(R.id.pickerTitle)?.text = "Select Trigger for Step $stepNumber"
        pickerDialog.findViewById<ImageView>(R.id.btnPickerClose)?.setOnClickListener { pickerDialog.dismiss() }

        fun bindOption(viewId: Int, triggerKey: String) {
            pickerDialog.findViewById<View>(viewId)?.setOnClickListener {
                onTriggerSelected(triggerKey)
                pickerDialog.dismiss()
            }
        }

        bindOption(R.id.optTriggerTouch, "TOUCH")
        bindOption(R.id.optTriggerProximity, "PROXIMITY")
        bindOption(R.id.optTriggerFaceDown, "FACE_DOWN")
        bindOption(R.id.optTriggerLight, "LIGHT")
        bindOption(R.id.optTriggerMagnetic, "MAGNETIC")
        bindOption(R.id.optTriggerTilt, "TILT")
        bindOption(R.id.optTriggerVolDown, "VOL_DOWN")
        bindOption(R.id.optTriggerAny, "ANY")

        pickerDialog.show()
    }

    fun showStepRevealPanelDialog(
        activity: Activity,
        settingsManager: SettingsManager,
        onActivateMagic: (String) -> Unit,
        onStatusChanged: () -> Unit = {}
    ) {
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_step_reveal_settings)

        dialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0.85f)
        }

        val swStepRevealEnable = dialog.findViewById<SwitchMaterial>(R.id.swStepRevealEnable)
        val btnClose = dialog.findViewById<ImageView>(R.id.btnCloseStepRevealDialog)
        val btnSave = dialog.findViewById<Button>(R.id.dialogBtnSaveStepSequence)

        // Presets
        val cardPresetAllTouch = dialog.findViewById<View>(R.id.cardPresetAllTouch)
        val cardPresetSensoryMix = dialog.findViewById<View>(R.id.cardPresetSensoryMix)
        val cardPresetPureAir = dialog.findViewById<View>(R.id.cardPresetPureAir)
        val cardPresetStealth = dialog.findViewById<View>(R.id.cardPresetStealth)

        // Step cards & text views
        val cardStep1 = dialog.findViewById<View>(R.id.cardStep1)
        val cardStep2 = dialog.findViewById<View>(R.id.cardStep2)
        val cardStep3 = dialog.findViewById<View>(R.id.cardStep3)
        val cardStep4 = dialog.findViewById<View>(R.id.cardStep4)

        val tvStep1Trigger = dialog.findViewById<TextView>(R.id.tvStep1Trigger)
        val tvStep1Subtitle = dialog.findViewById<TextView>(R.id.tvStep1Subtitle)
        val tvStep2Trigger = dialog.findViewById<TextView>(R.id.tvStep2Trigger)
        val tvStep2Subtitle = dialog.findViewById<TextView>(R.id.tvStep2Subtitle)
        val tvStep3Trigger = dialog.findViewById<TextView>(R.id.tvStep3Trigger)
        val tvStep3Subtitle = dialog.findViewById<TextView>(R.id.tvStep3Subtitle)
        val tvStep4Trigger = dialog.findViewById<TextView>(R.id.tvStep4Trigger)
        val tvStep4Subtitle = dialog.findViewById<TextView>(R.id.tvStep4Subtitle)

        // Parse existing 4 steps (step 5 is permanently TOUCH for Ghost Flight)
        val rawSeq = settingsManager.stepRevealSequence.split(",").map { it.trim().uppercase() }
        var step1 = rawSeq.getOrElse(0) { "TOUCH" }
        var step2 = rawSeq.getOrElse(1) { "PROXIMITY" }
        var step3 = rawSeq.getOrElse(2) { "FACE_DOWN" }
        var step4 = rawSeq.getOrElse(3) { "LIGHT" }

        swStepRevealEnable.isChecked = (settingsManager.activeMagic == "STEP_REVEAL")

        fun updateUI() {
            tvStep1Trigger.text = getTriggerDisplayName(step1)
            tvStep1Subtitle.text = getTriggerSubtitle(step1, 1)

            tvStep2Trigger.text = getTriggerDisplayName(step2)
            tvStep2Subtitle.text = getTriggerSubtitle(step2, 2)

            tvStep3Trigger.text = getTriggerDisplayName(step3)
            tvStep3Subtitle.text = getTriggerSubtitle(step3, 3)

            tvStep4Trigger.text = getTriggerDisplayName(step4)
            tvStep4Subtitle.text = getTriggerSubtitle(step4, 4)

            // Update preset highlights
            val current4 = "$step1,$step2,$step3,$step4"
            cardPresetAllTouch.setBackgroundResource(
                if (current4 == "TOUCH,TOUCH,TOUCH,TOUCH") R.drawable.bg_step_preset_active else R.drawable.bg_step_preset_normal
            )
            cardPresetSensoryMix.setBackgroundResource(
                if (current4 == "TOUCH,PROXIMITY,FACE_DOWN,LIGHT") R.drawable.bg_step_preset_active else R.drawable.bg_step_preset_normal
            )
            cardPresetPureAir.setBackgroundResource(
                if (current4 == "PROXIMITY,LIGHT,PROXIMITY,LIGHT") R.drawable.bg_step_preset_active else R.drawable.bg_step_preset_normal
            )
            cardPresetStealth.setBackgroundResource(
                if (current4 == "MAGNETIC,TILT,VOL_DOWN,TOUCH") R.drawable.bg_step_preset_active else R.drawable.bg_step_preset_normal
            )
        }

        updateUI()

        // 1-Tap Preset Handlers
        cardPresetAllTouch.setOnClickListener {
            step1 = "TOUCH"; step2 = "TOUCH"; step3 = "TOUCH"; step4 = "TOUCH"
            updateUI()
            MagicToast.show(activity, "⚡ Selected: All Touch Preset")
        }

        cardPresetSensoryMix.setOnClickListener {
            step1 = "TOUCH"; step2 = "PROXIMITY"; step3 = "FACE_DOWN"; step4 = "LIGHT"
            updateUI()
            MagicToast.show(activity, "🌪️ Selected: Sensory Mix Preset")
        }

        cardPresetPureAir.setOnClickListener {
            step1 = "PROXIMITY"; step2 = "LIGHT"; step3 = "PROXIMITY"; step4 = "LIGHT"
            updateUI()
            MagicToast.show(activity, "🌊 Selected: Pure Air Preset")
        }

        cardPresetStealth.setOnClickListener {
            step1 = "MAGNETIC"; step2 = "TILT"; step3 = "VOL_DOWN"; step4 = "TOUCH"
            updateUI()
            MagicToast.show(activity, "🧲 Selected: Stealth Ring Preset")
        }

        // 4 Step Card Click Handlers
        cardStep1.setOnClickListener {
            showTriggerPickerDialog(activity, 1) { newTrigger ->
                step1 = newTrigger
                updateUI()
            }
        }

        cardStep2.setOnClickListener {
            showTriggerPickerDialog(activity, 2) { newTrigger ->
                step2 = newTrigger
                updateUI()
            }
        }

        cardStep3.setOnClickListener {
            showTriggerPickerDialog(activity, 3) { newTrigger ->
                step3 = newTrigger
                updateUI()
            }
        }

        cardStep4.setOnClickListener {
            showTriggerPickerDialog(activity, 4) { newTrigger ->
                step4 = newTrigger
                updateUI()
            }
        }

        swStepRevealEnable.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                onActivateMagic("STEP_REVEAL")
            } else if (settingsManager.activeMagic == "STEP_REVEAL") {
                onActivateMagic("NONE")
            }
            onStatusChanged()
        }

        btnSave.setOnClickListener {
            val fullSequence = "$step1,$step2,$step3,$step4,TOUCH"
            settingsManager.stepRevealSequence = fullSequence
            if (swStepRevealEnable.isChecked) {
                onActivateMagic("STEP_REVEAL")
            }
            onStatusChanged()
            MagicToast.show(activity, "✅ Step Reveal Configuration Saved!")
            dialog.dismiss()
        }

        btnClose.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    fun showProfilePanelDialog(
        activity: Activity,
        onPermissionsCheck: () -> Unit,
        onSubscriptionRestored: () -> Unit
    ) {
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_profile_settings)

        dialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0.85f)
        }

        val profileAvatarInitials = dialog.findViewById<TextView>(R.id.profileAvatarInitials)
        val profileTxtEmail = dialog.findViewById<TextView>(R.id.profileTxtEmail)
        val cardOverlay = dialog.findViewById<View>(R.id.profileCardOverlayPermission)
        val cardBackup = dialog.findViewById<View>(R.id.profileCardCloudBackup)
        val cardPaymentHistory = dialog.findViewById<View>(R.id.profileCardPaymentHistory)
        val btnLogout = dialog.findViewById<Button>(R.id.profileBtnLogout)
        val btnClose = dialog.findViewById<ImageView>(R.id.btnCloseProfileDialog)
        val premiumBadge = dialog.findViewById<View>(R.id.profilePremiumBadge)
        val txtPremiumBadge = dialog.findViewById<TextView>(R.id.profileTxtPremiumBadge)

        cardPaymentHistory?.setOnClickListener {
            dialog.dismiss()
            activity.startActivity(Intent(activity, PaymentHistoryActivity::class.java))
        }

        val etSecretPasscode = dialog.findViewById<EditText>(R.id.profileEtSecretPasscode)
        val btnSaveSecretPasscode = dialog.findViewById<Button>(R.id.profileBtnSaveSecretPasscode)
        val settingsManager = SettingsManager(activity)

        etSecretPasscode?.setText(settingsManager.secretUnlockCode)
        btnSaveSecretPasscode?.setOnClickListener {
            val code = etSecretPasscode?.text?.toString()?.trim() ?: ""
            if (code.length == 3 && code.all { it.isDigit() }) {
                settingsManager.secretUnlockCode = code
                MagicToast.show(activity, "Secret Backdoor Code updated to '$code'!")
            } else {
                MagicToast.show(activity, "Code must be exactly 3 digits (e.g. 741)")
                etSecretPasscode?.setText(settingsManager.secretUnlockCode)
            }
        }

        val txtProfilePlanStatus = dialog.findViewById<TextView>(R.id.txtProfilePlanStatus)
        val txtProfilePurchaseType = dialog.findViewById<TextView>(R.id.txtProfilePurchaseType)
        val txtProfilePurchaseDate = dialog.findViewById<TextView>(R.id.txtProfilePurchaseDate)
        val txtProfileTransactionId = dialog.findViewById<TextView>(R.id.txtProfileTransactionId)
        val txtProfileExpiryDate = dialog.findViewById<TextView>(R.id.txtProfileExpiryDate)
        val txtProfileExpiryCountdown = dialog.findViewById<TextView>(R.id.txtProfileExpiryCountdown)
        val btnProfileUpgradeCard = dialog.findViewById<Button>(R.id.btnProfileUpgradeCard)

        val rowProfilePurchaseType = dialog.findViewById<View>(R.id.rowProfilePurchaseType)
        val rowProfilePurchaseDate = dialog.findViewById<View>(R.id.rowProfilePurchaseDate)
        val rowProfileTransactionId = dialog.findViewById<View>(R.id.rowProfileTransactionId)
        val rowProfileExpiry = dialog.findViewById<View>(R.id.rowProfileExpiry)

        val email = AuthManager.currentUser?.email ?: "guest@magicapp.com"
        profileTxtEmail.text = email
        if (email.isNotEmpty()) {
            profileAvatarInitials.text = email.take(1).uppercase()
        }

        fun triggerPaywall() {
            dialog.dismiss()
            activity.startActivity(Intent(activity, PremiumActivity::class.java))
        }

        if (PremiumManager.isUserPremium) {
            txtPremiumBadge.text = "PRO MEMBER"
            premiumBadge.setOnClickListener(null)
        } else {
            txtPremiumBadge.text = "UPGRADE TO PRO"
            premiumBadge.setOnClickListener { triggerPaywall() }
        }

        btnProfileUpgradeCard.setOnClickListener { triggerPaywall() }

        // Automatically sync and restore premium status in background when profile is viewed
        SubscriptionManager.syncAndRestorePremiumStatus(activity) { success, _ ->
            if (success) {
                activity.runOnUiThread {
                    onSubscriptionRestored()
                }
            }
        }

        val localSub = SubscriptionManager.getSubscriptionDetails(activity)
        if (localSub.isPremium) {
            txtProfilePlanStatus.text = localSub.planType
            txtProfilePlanStatus.setTextColor(Color.parseColor("#FFD700"))
            txtPremiumBadge.text = "PRO MEMBER"
            premiumBadge.setOnClickListener(null)

            rowProfilePurchaseType.visibility = View.VISIBLE
            rowProfilePurchaseDate.visibility = View.VISIBLE
            rowProfileTransactionId.visibility = View.VISIBLE
            rowProfileExpiry.visibility = View.VISIBLE
            btnProfileUpgradeCard.visibility = View.GONE

            txtProfilePurchaseType.text = if (localSub.purchaseType == "lifetime") "Lifetime License" else "Subscription Pass"
            txtProfilePurchaseDate.text = localSub.startDate
            txtProfileExpiryDate.text = localSub.expiryDate
            txtProfileTransactionId.text = localSub.paymentId

            if (localSub.purchaseType == "lifetime" || localSub.expiryMillis == 0L) {
                txtProfileExpiryCountdown.text = "Forever Unlocked (Lifetime)"
                txtProfileExpiryCountdown.setTextColor(Color.parseColor("#00FF66"))
            } else {
                val daysDiff = localSub.expiryMillis - System.currentTimeMillis()
                if (daysDiff <= 0) {
                    txtProfileExpiryCountdown.text = "Plan Expired"
                    txtProfileExpiryCountdown.setTextColor(Color.parseColor("#FF2D55"))
                } else {
                    val days = daysDiff / (1000 * 60 * 60 * 24)
                    val hours = (daysDiff % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60)
                    txtProfileExpiryCountdown.text = "Expires in $days Days, $hours Hours"
                    txtProfileExpiryCountdown.setTextColor(Color.parseColor("#FFD700"))
                }
            }
        } else {
            txtProfilePlanStatus.text = "CalcMagic Free Tier"
            txtProfilePlanStatus.setTextColor(Color.parseColor("#88FFFFFF"))
            txtPremiumBadge.text = "UPGRADE TO PRO"
            premiumBadge.setOnClickListener { triggerPaywall() }

            rowProfilePurchaseType.visibility = View.GONE
            rowProfilePurchaseDate.visibility = View.GONE
            rowProfileTransactionId.visibility = View.GONE
            rowProfileExpiry.visibility = View.GONE
            btnProfileUpgradeCard.visibility = View.VISIBLE
        }

        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            val listenerRegistration = FirebaseFirestore.getInstance()
                .collection("users")
                .document(user.uid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || !dialog.isShowing) return@addSnapshotListener
                    if (snapshot != null && snapshot.exists()) {
                        val isPremiumBool = snapshot.getBoolean("isPremium") == true
                        val isProUserBool = snapshot.getBoolean("isProUser") == true
                        val planType = snapshot.getString("planType") ?: ""
                        val transactionId = snapshot.getString("transactionId") ?: snapshot.getString("paymentId") ?: "N/A"
                        val premiumExpiryDate = snapshot.getLong("premiumExpiryDate") ?: snapshot.getLong("expiryMillis") ?: 0L
                        val activatedAt = snapshot.getLong("activatedAt") ?: snapshot.getLong("lastPaymentDate") ?: 0L
                        val startDateDoc = snapshot.getString("startDate")
                        val expiryDateDoc = snapshot.getString("expiryDate")
                        val purchaseType = snapshot.getString("purchaseType") ?: if (planType.contains("Lifetime", ignoreCase = true)) "lifetime" else "subscription"

                        val isPremium = isPremiumBool || isProUserBool ||
                                (planType.isNotEmpty() && !planType.equals("Free Tier", ignoreCase = true)) ||
                                (transactionId.isNotEmpty() && !transactionId.equals("N/A", ignoreCase = true))

                        val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

                        if (isPremium) {
                            txtProfilePlanStatus.text = if (planType.isNotEmpty()) planType else "CalcMagic Premium Pro"
                            txtProfilePlanStatus.setTextColor(Color.parseColor("#FFD700"))
                            txtPremiumBadge.text = "PRO MEMBER"
                            premiumBadge.setOnClickListener(null)

                            rowProfilePurchaseType.visibility = View.VISIBLE
                            rowProfilePurchaseDate.visibility = View.VISIBLE
                            rowProfileTransactionId.visibility = View.VISIBLE
                            rowProfileExpiry.visibility = View.VISIBLE
                            btnProfileUpgradeCard.visibility = View.GONE

                            txtProfileTransactionId.text = transactionId

                            if (!startDateDoc.isNullOrEmpty() && startDateDoc != "N/A") {
                                txtProfilePurchaseDate.text = startDateDoc
                            } else if (activatedAt > 0L) {
                                txtProfilePurchaseDate.text = dateFormat.format(Date(activatedAt))
                            }

                            if (purchaseType == "lifetime" || planType.contains("Lifetime", ignoreCase = true)) {
                                txtProfilePurchaseType.text = "Lifetime License"
                                txtProfileExpiryDate.text = "Never Expires (Lifetime Access)"
                                txtProfileExpiryCountdown.text = "Forever Unlocked (Lifetime)"
                                txtProfileExpiryCountdown.setTextColor(Color.parseColor("#00FF66"))
                            } else {
                                txtProfilePurchaseType.text = "Subscription Pass"
                                if (!expiryDateDoc.isNullOrEmpty() && expiryDateDoc != "N/A") {
                                    txtProfileExpiryDate.text = expiryDateDoc
                                } else if (premiumExpiryDate > 0L) {
                                    txtProfileExpiryDate.text = dateFormat.format(Date(premiumExpiryDate))
                                }

                                if (premiumExpiryDate > 0L) {
                                    val daysDiff = premiumExpiryDate - System.currentTimeMillis()
                                    if (daysDiff <= 0) {
                                        txtProfileExpiryCountdown.text = "Plan Expired"
                                        txtProfileExpiryCountdown.setTextColor(Color.parseColor("#FF2D55"))
                                    } else {
                                        val days = daysDiff / (1000 * 60 * 60 * 24)
                                        val hours = (daysDiff % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60)
                                        txtProfileExpiryCountdown.text = "Expires in $days Days, $hours Hours"
                                        txtProfileExpiryCountdown.setTextColor(Color.parseColor("#FFD700"))
                                    }
                                }
                            }
                        }
                    }
                }

            dialog.setOnDismissListener {
                listenerRegistration.remove()
            }
        }

        btnClose.setOnClickListener { dialog.dismiss() }

        cardOverlay.setOnClickListener {
            dialog.dismiss()
            onPermissionsCheck()
        }

        cardBackup.setOnClickListener {
            MagicToast.show(activity, "Configurations Synced to Cloud Securely!")
        }

        btnLogout.setOnClickListener {
            dialog.dismiss()
            AuthManager.logout()
            PremiumManager.clearLocalPremium(activity)
            com.magic.calculator.core.logic.OnboardingManager.clearLocalState(activity)
            val intent = Intent(activity, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            activity.startActivity(intent)
        }

        dialog.show()
    }

    fun showPerformanceGuideDialog(
        context: Context,
        title: String,
        steps: List<String>,
        activeColorHex: String,
        onPracticeClick: (() -> Unit)? = null
    ) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_performance_guide)

        dialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0.85f)
        }

        val txtTitle = dialog.findViewById<TextView>(R.id.guideTitle)
        val containerSteps = dialog.findViewById<LinearLayout>(R.id.guideStepsContainer)
        val btnPractice = dialog.findViewById<Button>(R.id.btnGuideStartPractice)
        val btnClose = dialog.findViewById<Button>(R.id.guideBtnClose)

        txtTitle.text = title
        txtTitle.setTextColor(Color.parseColor(activeColorHex))

        containerSteps.removeAllViews()
        for (step in steps) {
            val tv = TextView(context).apply {
                text = step
                setTextColor(Color.WHITE)
                textSize = 15f
                setPadding(0, 8, 0, 16)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            containerSteps.addView(tv)
        }

        if (onPracticeClick != null) {
            btnPractice?.visibility = View.VISIBLE
            btnPractice?.setOnClickListener {
                dialog.dismiss()
                onPracticeClick()
            }
        } else {
            btnPractice?.visibility = View.GONE
        }

        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    fun showContactForceGuideDialog(
        activity: Activity,
        settingsManager: SettingsManager,
        onActivateMagic: (String) -> Unit
    ) {
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_contact_force_guide)

        dialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0.85f)
        }

        val btnCloseGuide = dialog.findViewById<ImageView>(R.id.btnCloseGuide)
        val btnDismissGuide = dialog.findViewById<Button>(R.id.btnDismissGuide)
        val btnTestPredictionView = dialog.findViewById<Button>(R.id.btnTestPredictionView)

        btnCloseGuide?.setOnClickListener { dialog.dismiss() }
        btnDismissGuide?.setOnClickListener { dialog.dismiss() }

        btnTestPredictionView?.setOnClickListener {
            dialog.dismiss()
            onActivateMagic("CONTACT_FORCE")

            if (settingsManager.forcedContactName.isEmpty()) {
                settingsManager.forcedContactName = "Abha Gupta"
            }
            if (settingsManager.forcedContactPhone.isEmpty()) {
                settingsManager.forcedContactPhone = "+91 98765 43210"
            }

            settingsManager.appMode = 3
            val intent = Intent(activity, PhotoViewerActivity::class.java).apply {
                putExtra("EXTRA_IMAGE_NAME", "prediction_friends.jpg")
                putExtra("EXTRA_POSITION", 1)
                putExtra("EXTRA_TOTAL", 1)
            }
            activity.startActivity(intent)
        }

        dialog.show()
    }

    fun showContactForceConfigDialog(
        activity: Activity,
        settingsManager: SettingsManager,
        onActivateMagic: (String) -> Unit,
        onStatusChanged: () -> Unit
    ) {
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_contact_force_config)

        dialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0.85f)
        }

        val swEnable = dialog.findViewById<SwitchMaterial>(R.id.swEnableContactForce)
        val etName = dialog.findViewById<TextInputEditText>(R.id.etForcedContactName)
        val etPhone = dialog.findViewById<TextInputEditText>(R.id.etForcedContactPhone)
        val btnClose = dialog.findViewById<View>(R.id.btnCloseConfig)
        val btnSave = dialog.findViewById<Button>(R.id.btnSaveConfig)
        val btnTest = dialog.findViewById<Button>(R.id.btnTestPredictionView)

        swEnable?.isChecked = settingsManager.isContactForceEnabled
        etName?.setText(settingsManager.forcedContactName)
        etPhone?.setText(settingsManager.forcedContactPhone)

        swEnable?.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.isContactForceEnabled = isChecked
            onStatusChanged()
        }

        btnClose?.setOnClickListener { dialog.dismiss() }

        btnSave?.setOnClickListener {
            val name = etName?.text?.toString()?.trim() ?: ""
            val phone = etPhone?.text?.toString()?.trim() ?: ""
            if (name.isNotEmpty()) settingsManager.forcedContactName = name
            if (phone.isNotEmpty()) settingsManager.forcedContactPhone = phone
            val isChecked = swEnable?.isChecked ?: false

            if (isChecked) {
                onActivateMagic("CONTACT_FORCE")
            } else {
                if (settingsManager.activeMagic == "CONTACT_FORCE") {
                    onActivateMagic("NONE")
                }
            }

            onStatusChanged()
            dialog.dismiss()
        }

        btnTest?.setOnClickListener {
            dialog.dismiss()
            val name = etName?.text?.toString()?.trim() ?: ""
            val phone = etPhone?.text?.toString()?.trim() ?: ""
            if (name.isNotEmpty()) settingsManager.forcedContactName = name
            if (phone.isNotEmpty()) settingsManager.forcedContactPhone = phone
            onActivateMagic("CONTACT_FORCE")
            settingsManager.appMode = 3

            if (settingsManager.forcedContactName.isEmpty()) settingsManager.forcedContactName = "Abha Gupta"
            if (settingsManager.forcedContactPhone.isEmpty()) settingsManager.forcedContactPhone = "+91 98765 43210"

            val intent = Intent(activity, PhotoViewerActivity::class.java).apply {
                putExtra("EXTRA_IMAGE_NAME", "prediction_friends.jpg")
                putExtra("EXTRA_POSITION", 1)
                putExtra("EXTRA_TOTAL", 1)
            }
            activity.startActivity(intent)
        }

        dialog.show()
    }

    fun showToxicTimeConfigDialog(
        activity: Activity,
        settingsManager: SettingsManager,
        onActivateMagic: (String) -> Unit,
        onStatusChanged: () -> Unit
    ) {
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_toxic_time_settings)

        dialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0.85f)
        }

        val swEnable = dialog.findViewById<SwitchMaterial>(R.id.swToxicEnable)
        val rbTime = dialog.findViewById<RadioButton>(R.id.rbDialogToxicTime)
        val rbDate = dialog.findViewById<RadioButton>(R.id.rbDialogToxicDate)
        val rbBattery = dialog.findViewById<RadioButton>(R.id.rbDialogToxicBattery)
        val btnClose = dialog.findViewById<ImageView>(R.id.btnCloseToxicDialog)
        val btnSave = dialog.findViewById<Button>(R.id.dialogBtnSaveToxic)

        swEnable?.isChecked = (settingsManager.activeMagic == "TOXIC_TIME")
        when (settingsManager.toxicMode) {
            0 -> rbTime?.isChecked = true
            1 -> rbDate?.isChecked = true
            2 -> rbBattery?.isChecked = true
            else -> rbTime?.isChecked = true
        }

        btnClose?.setOnClickListener { dialog.dismiss() }

        btnSave?.setOnClickListener {
            val mode = when {
                rbTime?.isChecked == true -> 0
                rbDate?.isChecked == true -> 1
                rbBattery?.isChecked == true -> 2
                else -> 0
            }
            settingsManager.toxicMode = mode
            val isChecked = swEnable?.isChecked == true
            if (isChecked) {
                onActivateMagic("TOXIC_TIME")
            } else if (settingsManager.activeMagic == "TOXIC_TIME") {
                onActivateMagic("NONE")
            }
            onStatusChanged()
            dialog.dismiss()
        }

        dialog.show()
    }

    fun showGhostTypingConfigDialog(
        activity: Activity,
        settingsManager: SettingsManager,
        onActivateMagic: (String) -> Unit,
        onStatusChanged: () -> Unit
    ) {
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_ghost_typing_settings)

        dialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0.85f)
        }

        val swEnable = dialog.findViewById<SwitchMaterial>(R.id.swGhostEnable)
        val etGhostNumber = dialog.findViewById<EditText>(R.id.dialogEtGhostNumber)
        val etGhostDelay = dialog.findViewById<EditText>(R.id.dialogEtGhostDelay)
        val btnClose = dialog.findViewById<ImageView>(R.id.btnCloseGhostDialog)
        val btnSave = dialog.findViewById<Button>(R.id.dialogBtnSaveGhost)

        swEnable?.isChecked = (settingsManager.activeMagic == "GHOST_TYPING")
        etGhostNumber?.setText(settingsManager.ghostTypingNumber)
        etGhostDelay?.setText(settingsManager.ghostTypingDelay.toString())

        btnClose?.setOnClickListener { dialog.dismiss() }

        btnSave?.setOnClickListener {
            val num = etGhostNumber?.text?.toString()?.trim() ?: ""
            val delay = etGhostDelay?.text?.toString()?.toIntOrNull() ?: 5
            if (num.isNotEmpty()) settingsManager.ghostTypingNumber = num
            settingsManager.ghostTypingDelay = delay

            val isChecked = swEnable?.isChecked == true
            if (isChecked) {
                onActivateMagic("GHOST_TYPING")
            } else if (settingsManager.activeMagic == "GHOST_TYPING") {
                onActivateMagic("NONE")
            }
            onStatusChanged()
            dialog.dismiss()
        }

        dialog.show()
    }

    fun showGhostRecoveryConfigDialog(
        activity: Activity,
        settingsManager: SettingsManager,
        onActivateMagic: (String) -> Unit,
        onStatusChanged: () -> Unit
    ) {
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_ghost_recovery_settings)

        dialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0.85f)
        }

        val swEnable = dialog.findViewById<SwitchMaterial>(R.id.swRecoveryEnable)
        val btnClose = dialog.findViewById<ImageView>(R.id.btnCloseRecoveryDialog)
        val btnSave = dialog.findViewById<Button>(R.id.dialogBtnSaveRecovery)

        swEnable?.isChecked = (settingsManager.activeMagic == "REAPPEARING_RESULT")

        btnClose?.setOnClickListener { dialog.dismiss() }

        btnSave?.setOnClickListener {
            val isChecked = swEnable?.isChecked == true
            if (isChecked) {
                onActivateMagic("REAPPEARING_RESULT")
            } else if (settingsManager.activeMagic == "REAPPEARING_RESULT") {
                onActivateMagic("NONE")
            }
            onStatusChanged()
            dialog.dismiss()
        }

        dialog.show()
    }

    fun showSubmitIdeaDialog(activity: Activity) {
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_submit_magic_idea)

        dialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0.85f)
        }

        val btnClose = dialog.findViewById<ImageView>(R.id.btnCloseIdeaDialog)
        val chipNewTrick = dialog.findViewById<TextView>(R.id.chipNewTrick)
        val chipQuery = dialog.findViewById<TextView>(R.id.chipQuery)
        val chipBug = dialog.findViewById<TextView>(R.id.chipBug)
        val chipImprovement = dialog.findViewById<TextView>(R.id.chipImprovement)
        val etTitle = dialog.findViewById<EditText>(R.id.etIdeaTitle)
        val etDesc = dialog.findViewById<EditText>(R.id.etIdeaDescription)
        val btnWhatsApp = dialog.findViewById<Button>(R.id.btnDirectWhatsApp)

        var selectedCategory = "🎩 New Trick Idea"

        fun updateChips() {
            val selectedColor = Color.parseColor("#25D366")
            val unselectedColor = Color.parseColor("#BBFFFFFF")

            chipNewTrick.setTextColor(if (selectedCategory == "🎩 New Trick Idea") selectedColor else unselectedColor)
            chipQuery.setTextColor(if (selectedCategory == "❓ Question / Query") selectedColor else unselectedColor)
            chipBug.setTextColor(if (selectedCategory == "🐛 Bug Report") selectedColor else unselectedColor)
            chipImprovement.setTextColor(if (selectedCategory == "⚡ Feature Improvement") selectedColor else unselectedColor)
        }

        chipNewTrick.setOnClickListener { selectedCategory = "🎩 New Trick Idea"; updateChips() }
        chipQuery.setOnClickListener { selectedCategory = "❓ Question / Query"; updateChips() }
        chipBug.setOnClickListener { selectedCategory = "🐛 Bug Report"; updateChips() }
        chipImprovement.setOnClickListener { selectedCategory = "⚡ Feature Improvement"; updateChips() }

        btnClose.setOnClickListener { dialog.dismiss() }

        btnWhatsApp.setOnClickListener {
            val title = etTitle.text.toString().trim()
            val desc = etDesc.text.toString().trim()

            if (title.isEmpty()) {
                etTitle.error = "Please enter a subject or title"
                etTitle.requestFocus()
                return@setOnClickListener
            }
            if (desc.isEmpty()) {
                etDesc.error = "Please describe your idea or question"
                etDesc.requestFocus()
                return@setOnClickListener
            }

            val targetNumber = "918815114272"
            val message = """
                🪄 *Magic Calculator — New Idea / Query*
                
                📌 *Category:* $selectedCategory
                📝 *Subject:* $title
                
                💬 *Details:*
                $desc
                
                📱 *Device:* ${Build.MANUFACTURER} ${Build.MODEL}
                ✨ *App Version:* 3.0
            """.trimIndent()

            // Also save a background backup copy in Firestore
            try {
                val ideaData = hashMapOf(
                    "category" to selectedCategory,
                    "title" to title,
                    "description" to desc,
                    "whatsappSent" to true,
                    "targetNumber" to targetNumber,
                    "userId" to (AuthManager.currentUid ?: "anonymous"),
                    "userEmail" to (AuthManager.currentUser?.email ?: ""),
                    "deviceInfo" to "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})",
                    "appVersion" to "3.0",
                    "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    "status" to "UNREAD"
                )
                FirebaseFirestore.getInstance().collection("magic_ideas").add(ideaData)
            } catch (e: Exception) {
                // Ignore backup error
            }

            val url = "https://api.whatsapp.com/send?phone=$targetNumber&text=${android.net.Uri.encode(message)}"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = android.net.Uri.parse(url)
            }

            try {
                activity.startActivity(intent)
                dialog.dismiss()
            } catch (e: Exception) {
                try {
                    val genericIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                    activity.startActivity(genericIntent)
                    dialog.dismiss()
                } catch (ex: Exception) {
                    etDesc.error = "WhatsApp not found on device"
                }
            }
        }

        dialog.show()
    }

    fun showWallpaperMagicConfigDialog(
        activity: Activity,
        settingsManager: SettingsManager,
        onActivateMagic: (String) -> Unit = {},
        onStatusChanged: () -> Unit
    ) {
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_wallpaper_magic_config)

        dialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0.85f)
        }

        val swEnableWallpaper = dialog.findViewById<SwitchMaterial>(R.id.swEnableWallpaperMagic)
        val swEnableVoice = dialog.findViewById<SwitchMaterial>(R.id.swEnableVoiceListener)
        val rgTrigger = dialog.findViewById<RadioGroup>(R.id.rgWallpaperTriggerMethod)
        val rbTrigVolHold = dialog.findViewById<RadioButton>(R.id.rbTrigVolHold)
        val rbTrigFaceDown = dialog.findViewById<RadioButton>(R.id.rbTrigFaceDown)
        val rbTrigMagnetic = dialog.findViewById<RadioButton>(R.id.rbTrigMagnetic)

        val rgTarget = dialog.findViewById<RadioGroup>(R.id.rgWallpaperTarget)
        val rbBoth = dialog.findViewById<RadioButton>(R.id.rbTargetBoth)
        val rbHome = dialog.findViewById<RadioButton>(R.id.rbTargetHome)
        val rbLock = dialog.findViewById<RadioButton>(R.id.rbTargetLock)

        val rgStyle = dialog.findViewById<RadioGroup>(R.id.rgWallpaperStyle)
        val rbNeon = dialog.findViewById<RadioButton>(R.id.rbStyleNeon)
        val rbGold = dialog.findViewById<RadioButton>(R.id.rbStyleGold)
        val rbCosmic = dialog.findViewById<RadioButton>(R.id.rbStyleCosmic)

        val rgContentType = dialog.findViewById<RadioGroup>(R.id.rgPredictionContentType)
        val rbModeNumber = dialog.findViewById<RadioButton>(R.id.rbModeNumber)
        val rbModeCard = dialog.findViewById<RadioButton>(R.id.rbModeCard)
        val tilForcedNumber = dialog.findViewById<View>(R.id.tilForcedNumber)
        val tilForcedCard = dialog.findViewById<View>(R.id.tilForcedCard)
        val etForcedCard = dialog.findViewById<TextInputEditText>(R.id.etForcedWallpaperCard)

        val etNumber = dialog.findViewById<TextInputEditText>(R.id.etForcedWallpaperNumber)
        val btnTestApply = dialog.findViewById<Button>(R.id.btnTestApplyWallpaper)
        val btnTestVoice = dialog.findViewById<Button>(R.id.btnTestVoiceListen)
        val btnReset = dialog.findViewById<Button>(R.id.btnResetWallpaper)
        val btnSave = dialog.findViewById<Button>(R.id.btnSaveWallpaperConfig)
        val btnClose = dialog.findViewById<ImageView>(R.id.btnCloseWallpaperConfig)

        fun updateContentTypeVisibility() {
            tilForcedNumber?.visibility = View.VISIBLE
            tilForcedCard?.visibility = View.GONE
        }
        updateContentTypeVisibility()

        // Strictly enforce Number Mode (Cards is locked for next version)
        rbModeCard?.isEnabled = false
        rbModeCard?.isClickable = false
        rbModeNumber?.isChecked = true

        // Load initial state
        swEnableWallpaper?.isChecked = settingsManager.isWallpaperMagicEnabled
        swEnableVoice?.isChecked = settingsManager.isVoiceListenerEnabled
        etNumber?.setText(settingsManager.forcedWallpaperNumber)
        settingsManager.wallpaperContentType = "NUMBER"

        when (settingsManager.wallpaperTriggerMethod) {
            "FACE_DOWN" -> rbTrigFaceDown?.isChecked = true
            "MAGNETIC" -> rbTrigMagnetic?.isChecked = true
            else -> rbTrigVolHold?.isChecked = true
        }

        when (settingsManager.wallpaperMagicTarget) {
            1 -> rbHome?.isChecked = true
            2 -> rbLock?.isChecked = true
            else -> rbBoth?.isChecked = true
        }

        when (settingsManager.wallpaperStyleIndex) {
            1 -> rbGold?.isChecked = true
            2 -> rbCosmic?.isChecked = true
            else -> rbNeon?.isChecked = true
        }

        btnClose?.setOnClickListener { dialog.dismiss() }

        fun getSelectedTarget(): WallpaperMagicManager.Target {
            return when {
                rbHome?.isChecked == true -> WallpaperMagicManager.Target.HOME
                rbLock?.isChecked == true -> WallpaperMagicManager.Target.LOCK
                else -> WallpaperMagicManager.Target.BOTH
            }
        }

        fun getSelectedStyle(): Int {
            return when {
                rbGold?.isChecked == true -> 1
                rbCosmic?.isChecked == true -> 2
                else -> 0
            }
        }

        btnTestApply?.setOnClickListener {
            val target = getSelectedTarget()
            val style = getSelectedStyle()
            val isCardMode = rbModeCard?.isChecked == true
            CoroutineScope(Dispatchers.Main).launch {
                btnTestApply.isEnabled = false
                btnTestApply.text = "Applying..."
                val success = if (isCardMode) {
                    val cardCode = etForcedCard?.text?.toString()?.trim() ?: "AS"
                    WallpaperMagicManager.applyCardWallpaper(activity, cardCode, style, target)
                } else {
                    val num = etNumber?.text?.toString()?.trim() ?: "74"
                    WallpaperMagicManager.applyNumberWallpaper(activity, num, style, target)
                }
                btnTestApply.isEnabled = true
                btnTestApply.text = "🚀 Test Apply"
                if (success) {
                    MagicToast.show(activity, "✨ Wallpaper updated successfully!")
                } else {
                    MagicToast.show(activity, "Failed to update wallpaper")
                }
            }
        }

        val tvVoiceMicStatus = dialog.findViewById<TextView>(R.id.tvVoiceMicStatus)
        val pbVoiceAudioLevel = dialog.findViewById<ProgressBar>(R.id.pbVoiceAudioLevel)
        val tvVoiceRawTranscript = dialog.findViewById<TextView>(R.id.tvVoiceRawTranscript)
        val tvVoiceExtractedResult = dialog.findViewById<TextView>(R.id.tvVoiceExtractedResult)
        val tvVoiceDiagInfo = dialog.findViewById<TextView>(R.id.tvVoiceDiagInfo)

        val captureManager = activity.appContainer.voiceCaptureManager
        var testSession: VoiceCaptureManager.SessionHandle? = null
        var isTestListening = false

        fun resetTestVoiceUi() {
            isTestListening = false
            testSession = null
            btnTestVoice?.text = "🎙️ Test Voice"
            tvVoiceMicStatus?.text = "⚪ Idle"
            tvVoiceMicStatus?.setTextColor(Color.parseColor("#94A3B8"))
            pbVoiceAudioLevel?.progress = 0
        }

        btnTestVoice?.setOnClickListener {
            testSession?.let { session ->
                captureManager.release(session)
                resetTestVoiceUi()
                return@setOnClickListener
            }

            if (!MicPermission.ensure(activity)) {
                return@setOnClickListener
            }

            isTestListening = true
            btnTestVoice?.text = "🛑 Stop Listening"
            tvVoiceMicStatus?.text = "🎙️ Connecting..."
            tvVoiceMicStatus?.setTextColor(Color.parseColor("#00FFFF"))
            tvVoiceRawTranscript?.text = "Waiting for speech... speak into mic"
            tvVoiceExtractedResult?.text = "🎯 Matched: (None yet)"
            tvVoiceDiagInfo?.text = "Microphone initialized."

            val selectedMode = if (rbModeCard?.isChecked == true) "CARD" else "NUMBER"
            // timeoutMs = 0: the magician drives this one by hand, so silence must not end it.
            testSession = captureManager.requestSession(
                caller = VoiceCaptureManager.Caller.DIALOG_TEST,
                mode = selectedMode,
                timeoutMs = 0L,
                onStateChanged = { state ->
                    activity.runOnUiThread {
                        tvVoiceMicStatus?.text = state
                        if (state.contains("Active") || state.contains("Speaking")) {
                            tvVoiceMicStatus?.setTextColor(Color.parseColor("#10B981"))
                        } else if (state.contains("Listening")) {
                            tvVoiceMicStatus?.setTextColor(Color.parseColor("#00FFFF"))
                        }
                    }
                },
                onRmsChanged = { rms ->
                    activity.runOnUiThread {
                        pbVoiceAudioLevel?.progress = (rms * 2f).toInt().coerceIn(0, 25)
                    }
                },
                onRawTranscript = { transcript ->
                    activity.runOnUiThread {
                        tvVoiceRawTranscript?.text = "\"$transcript\""
                        if (selectedMode == "CARD") {
                            val parsedCard = VoiceCardExtractor.extractCard(transcript)
                            if (parsedCard != null) {
                                tvVoiceExtractedResult?.text = "🃏 Card: ${parsedCard.englishName} (${parsedCard.hindiName})"
                                tvVoiceExtractedResult?.setTextColor(Color.parseColor("#10B981"))
                            } else {
                                tvVoiceExtractedResult?.text = "🎯 Listening for Card (e.g. 'Heart 1', 'Queen of Hearts')..."
                                tvVoiceExtractedResult?.setTextColor(Color.parseColor("#F59E0B"))
                            }
                        } else {
                            val parsedNum = VoiceNumberExtractor.extractNumber(transcript)
                            if (parsedNum != null) {
                                tvVoiceExtractedResult?.text = "🎯 Number: $parsedNum"
                                tvVoiceExtractedResult?.setTextColor(Color.parseColor("#10B981"))
                            } else {
                                tvVoiceExtractedResult?.text = "🎯 Listening for Number (e.g. '63', '74')..."
                                tvVoiceExtractedResult?.setTextColor(Color.parseColor("#F59E0B"))
                            }
                        }
                    }
                },
                onErrorDetail = { errName, _ ->
                    activity.runOnUiThread {
                        tvVoiceDiagInfo?.text = "⚠️ $errName"
                    }
                },
                onCardCaptured = { card ->
                    activity.runOnUiThread {
                        isTestListening = false
                        rbModeCard?.isChecked = true
                        updateContentTypeVisibility()
                        etForcedCard?.setText(card.code)
                        btnTestVoice.text = "🎙️ Test Voice"
                        tvVoiceRawTranscript?.text = "🎉 Captured: \"${card.englishName}\""
                        tvVoiceExtractedResult?.text = "✨ APPLIED CARD: ${card.englishName} (${card.hindiName})"
                        tvVoiceExtractedResult?.setTextColor(Color.parseColor("#10B981"))
                        tvVoiceDiagInfo?.text = "✅ Success! Playing card extracted and wallpaper set."
                        pbVoiceAudioLevel?.progress = 0
                        MagicToast.show(activity, "🃏 Heard: '${card.englishName}'! Updating wallpaper...")

                        val target = getSelectedTarget()
                        val style = getSelectedStyle()
                        CoroutineScope(Dispatchers.Main).launch {
                            WallpaperMagicManager.applyCardWallpaper(activity, card.code, style, target)
                        }
                    }
                },
                onNumberCaptured = { detectedNum ->
                    activity.runOnUiThread {
                        isTestListening = false
                        rbModeNumber?.isChecked = true
                        updateContentTypeVisibility()
                        etNumber?.setText(detectedNum)
                        btnTestVoice.text = "🎙️ Test Voice"
                        tvVoiceRawTranscript?.text = "🎉 Captured: \"$detectedNum\""
                        tvVoiceExtractedResult?.text = "✨ APPLIED NUMBER: $detectedNum"
                        tvVoiceExtractedResult?.setTextColor(Color.parseColor("#10B981"))
                        tvVoiceDiagInfo?.text = "✅ Success! Number extracted and wallpaper set."
                        pbVoiceAudioLevel?.progress = 0
                        MagicToast.show(activity, "🎯 Heard: '$detectedNum'! Updating wallpaper...")

                        val target = getSelectedTarget()
                        val style = getSelectedStyle()
                        CoroutineScope(Dispatchers.Main).launch {
                            WallpaperMagicManager.applyNumberWallpaper(activity, detectedNum, style, target)
                        }
                    }
                },
                onEnded = { reason ->
                    activity.runOnUiThread {
                        if (reason == VoiceCaptureManager.EndReason.CAPTURED) {
                            testSession = null
                        } else {
                            resetTestVoiceUi()
                            tvVoiceDiagInfo?.text = when (reason) {
                                VoiceCaptureManager.EndReason.PERMISSION_DENIED ->
                                    "❌ Microphone permission revoked — grant it and tap Test Voice again."
                                VoiceCaptureManager.EndReason.MIC_BUSY ->
                                    "❌ Mic busy — another app holds the microphone. Close it and tap again."
                                VoiceCaptureManager.EndReason.REJECTED ->
                                    "⚠️ Another capture is using the microphone right now."
                                else -> "⚪ Session closed"
                            }
                        }
                    }
                }
            )
            if (testSession == null) {
                resetTestVoiceUi()
                tvVoiceMicStatus?.text = "⚪ Blocked"
                tvVoiceDiagInfo?.text = "⚠️ Another capture owns the microphone — stop it first."
            }
        }

        val btnGoogleSpeechSheet = dialog.findViewById<Button>(R.id.btnGoogleSpeechSheet)
        btnGoogleSpeechSheet?.setOnClickListener {
            try {
                // The stock Google sheet is a third SpeechRecognizer client in this process. Hand
                // our own session back first so it never competes with ours for the microphone.
                testSession?.let { captureManager.release(it) }
                resetTestVoiceUi()
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak any number (e.g. 63, 74, बहत्तर)...")
                }
                activity.startActivityForResult(intent, 5544)
            } catch (e: Exception) {
                MagicToast.show(activity, "Could not open Google Speech: ${e.message}")
            }
        }

        val btnBattery = dialog.findViewById<Button>(R.id.btnWallpaperBatteryExemption)

        fun updateBatteryButtonState() {
            val powerManager = activity.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            val isIgnoring = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                powerManager?.isIgnoringBatteryOptimizations(activity.packageName) == true
            } else {
                true
            }
            if (isIgnoring) {
                btnBattery?.text = "✅ Unrestricted Background Active (No Sleep)"
                btnBattery?.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#059669"))
            } else {
                btnBattery?.text = "🔋 Allow Unrestricted Background (Fix Mic Sleep)"
                btnBattery?.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#B45309"))
            }
        }
        updateBatteryButtonState()

        btnBattery?.setOnClickListener {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                try {
                    val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = android.net.Uri.parse("package:${activity.packageName}")
                    }
                    activity.startActivity(intent)
                } catch (e: Exception) {
                    try {
                        activity.startActivity(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    } catch (e2: Exception) {
                        activity.startActivity(Intent(
                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            android.net.Uri.parse("package:${activity.packageName}")
                        ))
                    }
                }
            } else {
                MagicToast.show(activity, "Background activity is already unrestricted on your Android version.")
            }
        }

        btnReset?.setOnClickListener {
            CoroutineScope(Dispatchers.Main).launch {
                btnReset.isEnabled = false
                WallpaperMagicManager.resetToDefaultWallpaper(activity)
                btnReset.isEnabled = true
                MagicToast.show(activity, "🔄 Clean default wallpaper restored!")
            }
        }

        btnSave?.setOnClickListener {
            val isEnabled = (swEnableWallpaper?.isChecked == true) || (swEnableVoice?.isChecked == true)
            settingsManager.isWallpaperMagicEnabled = swEnableWallpaper?.isChecked == true
            settingsManager.isVoiceListenerEnabled = swEnableVoice?.isChecked == true
            settingsManager.wallpaperContentType = if (rbModeCard?.isChecked == true) "CARD" else "NUMBER"
            settingsManager.forcedWallpaperNumber = etNumber?.text?.toString()?.trim() ?: "74"
            settingsManager.forcedWallpaperCard = etForcedCard?.text?.toString()?.trim() ?: "AS"
            settingsManager.wallpaperMagicTarget = when {
                rbHome?.isChecked == true -> 1
                rbLock?.isChecked == true -> 2
                else -> 0
            }
            settingsManager.wallpaperStyleIndex = getSelectedStyle()
            settingsManager.wallpaperTriggerMethod = when {
                rbTrigFaceDown?.isChecked == true -> "FACE_DOWN"
                rbTrigMagnetic?.isChecked == true -> "MAGNETIC"
                else -> "VOL_HOLD"
            }

            if (isEnabled) {
                onActivateMagic("WALLPAPER_MAGIC")
            } else if (settingsManager.activeMagic == "WALLPAPER_MAGIC") {
                onActivateMagic("NONE")
            }

            onStatusChanged()
            MagicToast.show(activity, "💾 Wallpaper Magic Configuration Saved!")
            testSession?.let { captureManager.release(it) }
            testSession = null
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            testSession?.let { captureManager.release(it) }
            testSession = null
        }

        dialog.show()
    }
}
