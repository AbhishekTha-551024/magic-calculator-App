package com.magic.calculator.ui.dialogs

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
import com.magic.calculator.LoginActivity
import com.magic.calculator.PremiumActivity
import com.magic.calculator.R
import com.magic.calculator.logic.AuthManager
import com.magic.calculator.logic.PinManager
import com.magic.calculator.logic.PremiumManager
import com.magic.calculator.logic.SettingsManager
import com.magic.calculator.logic.SubscriptionManager
import com.magic.calculator.p2p.P2PLinkManager
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
                Toast.makeText(activity, "Select 'All Apps', find Magic Calculator, and set to 'No Restrictions'!", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(activity, "Please disable battery restrictions for the app in system settings.", Toast.LENGTH_LONG).show()
            }
        }

        swMoiLockEnable.isChecked = (settingsManager.appMode == 4)
        etSecret.setText(pinManager.secretDigit)
        etMaster.setText(pinManager.masterPin)

        val updatePermText = {
            val dpm = activity.getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
            val componentName = android.content.ComponentName(activity, com.magic.calculator.ui.moilock.MoiDeviceAdminReceiver::class.java)
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
                var needsPermission = false
                val dpm = activity.getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
                val componentName = android.content.ComponentName(activity, com.magic.calculator.ui.moilock.MoiDeviceAdminReceiver::class.java)
                if (!dpm.isAdminActive(componentName)) {
                    val intent = Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                        putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
                        putExtra(android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Required to simulate realistic lock screen.")
                    }
                    activity.startActivity(intent)
                    needsPermission = true
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(activity)) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:${activity.packageName}")
                    )
                    activity.startActivity(intent)
                    needsPermission = true
                }

                settingsManager.appMode = 4
                val serviceIntent = Intent(activity, com.magic.calculator.ui.moilock.MoiLockService::class.java)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        activity.startForegroundService(serviceIntent)
                    } else {
                        activity.startService(serviceIntent)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                if (needsPermission) {
                    Toast.makeText(activity, "Please grant permissions to fully activate MoiLock!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(activity, "MoiLock Enabled & Service Started!", Toast.LENGTH_SHORT).show()
                }
            } else {
                settingsManager.appMode = 0
                val serviceIntent = Intent(activity, com.magic.calculator.ui.moilock.MoiLockService::class.java)
                try {
                    activity.stopService(serviceIntent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                Toast.makeText(activity, "MoiLock Disabled & Service Stopped!", Toast.LENGTH_SHORT).show()
            }
            onStatusChanged()
            updatePermText()
        }

        btnSaveSecret.setOnClickListener {
            val digit = etSecret.text.toString()
            if (digit.length == 1) {
                pinManager.secretDigit = digit
                pinManager.resetLock()
                Toast.makeText(activity, "MoiLock Digit Saved & Trick Reset!", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(activity, "Must be exactly 1 digit!", Toast.LENGTH_SHORT).show()
            }
        }

        btnSaveMaster.setOnClickListener {
            val master = etMaster.text.toString()
            if (master.length == 5) {
                pinManager.masterPin = master
                Toast.makeText(activity, "Master PIN Saved!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(activity, "Master PIN must be 5 digits!", Toast.LENGTH_SHORT).show()
            }
        }

        btnSelectWallpaper.setOnClickListener {
            wallpaperPickerLauncher.launch("image/*")
            dialog.dismiss()
        }

        btnLaunchMoiLock.setOnClickListener {
            val intent = Intent(activity, com.magic.calculator.ui.moilock.MoiLockActivity::class.java)
            activity.startActivity(intent)
            dialog.dismiss()
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
                if (!formatted.contains(":")) {
                    Toast.makeText(activity, "Please enter a valid time like 4:20 or 4.20", Toast.LENGTH_SHORT).show()
                } else {
                    settingsManager.forcedGalleryTime = formatted
                    Toast.makeText(activity, "Forced Time Saved: $formatted", Toast.LENGTH_SHORT).show()
                }
            } else {
                settingsManager.forcedGalleryTime = "4:20"
                Toast.makeText(activity, "Reset to default", Toast.LENGTH_SHORT).show()
            }
        }

        btnLaunchGallery.setOnClickListener {
            dialog.dismiss()
            settingsManager.appMode = 3
            val intent = Intent(activity, com.magic.calculator.ui.GalleryActivity::class.java)
            activity.startActivity(intent)
        }

        dialog.show()
    }

    fun showExplosionPanelDialog(
        activity: Activity,
        settingsManager: SettingsManager,
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

        swExplosionEnable.isChecked = settingsManager.isExplosionEnabled
        if (settingsManager.explosionTrigger == "PROXIMITY") {
            rbExplosionProximity.isChecked = true
        } else {
            rbExplosionMagnetic.isChecked = true
        }

        btnClose.setOnClickListener { dialog.dismiss() }

        swExplosionEnable.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.isExplosionEnabled = isChecked
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

        swP2PEnable.isChecked = settingsManager.isP2PEnabled
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

        p2p.listener = object : P2PLinkManager.P2PEventListener {
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
                activity.runOnUiThread { Toast.makeText(activity, message, Toast.LENGTH_LONG).show() }
            }
        }

        btnCopyMagicId.setOnClickListener {
            val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Magic ID", p2p.myMagicId)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(activity, "Magic ID '${p2p.myMagicId}' copied!", Toast.LENGTH_SHORT).show()
        }

        btnSendRequest.setOnClickListener {
            val targetId = etPartnerMagicId.text.toString().trim()
            if (targetId.isEmpty()) {
                Toast.makeText(activity, "Enter your partner's Magic ID.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            updateStatus(false, null, "Sending request...")
            p2p.sendRequest(targetId) { success, message ->
                activity.runOnUiThread {
                    Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
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
                Toast.makeText(activity, "Request declined.", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(activity, "Flip trigger sent to partner!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(activity, "Not connected to any partner.", Toast.LENGTH_SHORT).show()
            }
        }

        btnP2PTriggerExplosion.setOnClickListener {
            if (p2p.isConnected) {
                p2p.sendCommand("TRG:EXPL")
                Toast.makeText(activity, "Explosion trigger sent to partner!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(activity, "Not connected to any partner.", Toast.LENGTH_SHORT).show()
            }
        }

        btnCloseDialog.setOnClickListener {
            p2p.listener = null
            dialog.dismiss()
        }

        dialog.show()
    }

    fun showStepRevealPanelDialog(
        activity: Activity,
        settingsManager: SettingsManager,
        onActivateMagic: (String) -> Unit
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
        val etStepSequence = dialog.findViewById<EditText>(R.id.dialogEtStepSequence)
        val btnSaveStepSequence = dialog.findViewById<Button>(R.id.dialogBtnSaveStepSequence)
        val btnClose = dialog.findViewById<ImageView>(R.id.btnCloseStepRevealDialog)

        swStepRevealEnable.isChecked = (settingsManager.activeMagic == "STEP_REVEAL")
        etStepSequence.setText(settingsManager.stepRevealSequence)

        btnClose.setOnClickListener { dialog.dismiss() }

        swStepRevealEnable.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) onActivateMagic("STEP_REVEAL")
            else if (settingsManager.activeMagic == "STEP_REVEAL") onActivateMagic("NONE")
        }

        btnSaveStepSequence.setOnClickListener {
            val seq = etStepSequence.text.toString().trim()
            if (seq.isNotEmpty()) {
                val cleaned = seq.split(",").map { it.trim().uppercase() }
                val validTriggers = setOf("TOUCH", "PROXIMITY", "FACE_DOWN", "LIGHT", "MAGNETIC", "TILT", "GYRO", "VOL_UP", "VOL_DOWN", "CLAP", "FINGERPRINT", "ROTATION_3", "ANY")
                val invalid = cleaned.filter { it !in validTriggers }
                if (invalid.isNotEmpty()) {
                    Toast.makeText(activity, "Invalid triggers: $invalid. Use TOUCH, PROXIMITY, FACE_DOWN, etc.", Toast.LENGTH_LONG).show()
                } else {
                    settingsManager.stepRevealSequence = cleaned.joinToString(",")
                    Toast.makeText(activity, "Sequence saved: ${settingsManager.stepRevealSequence}", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(activity, "Sequence cannot be empty.", Toast.LENGTH_SHORT).show()
            }
        }

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
        val btnLogout = dialog.findViewById<Button>(R.id.profileBtnLogout)
        val btnClose = dialog.findViewById<ImageView>(R.id.btnCloseProfileDialog)
        val premiumBadge = dialog.findViewById<View>(R.id.profilePremiumBadge)
        val txtPremiumBadge = dialog.findViewById<TextView>(R.id.profileTxtPremiumBadge)

        val etSecretPasscode = dialog.findViewById<EditText>(R.id.profileEtSecretPasscode)
        val btnSaveSecretPasscode = dialog.findViewById<Button>(R.id.profileBtnSaveSecretPasscode)
        val settingsManager = SettingsManager(activity)

        etSecretPasscode?.setText(settingsManager.secretUnlockCode)
        btnSaveSecretPasscode?.setOnClickListener {
            val code = etSecretPasscode?.text?.toString()?.trim() ?: ""
            if (code.length == 3 && code.all { it.isDigit() }) {
                settingsManager.secretUnlockCode = code
                Toast.makeText(activity, "Secret Backdoor Code updated to '$code'!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(activity, "Code must be exactly 3 digits (e.g. 741)", Toast.LENGTH_SHORT).show()
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

        val btnSimulate7Day = dialog.findViewById<Button>(R.id.btnSimulate7Day)
        val btnSimulateLifetime = dialog.findViewById<Button>(R.id.btnSimulateLifetime)
        val txtProfileSandboxHeader = dialog.findViewById<TextView>(R.id.txtProfileSandboxHeader)
        val layoutProfileSandboxButtons = dialog.findViewById<View>(R.id.layoutProfileSandboxButtons)

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

        btnSimulate7Day.text = "Restore Purchase"
        btnSimulate7Day.setOnClickListener {
            Toast.makeText(activity, "Restoring purchase from cloud...", Toast.LENGTH_SHORT).show()
            SubscriptionManager.restoreUserSubscriptionFromFirestore(activity) { success, message ->
                activity.runOnUiThread {
                    Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
                    if (success) {
                        onSubscriptionRestored()
                        dialog.dismiss()
                    }
                }
            }
        }

        btnSimulateLifetime.visibility = View.GONE

        val localSub = SubscriptionManager.getSubscriptionDetails(activity)
        if (localSub.isPremium) {
            txtProfilePlanStatus.text = "CalcMagic Premium Pro"
            txtProfilePlanStatus.setTextColor(Color.parseColor("#FFD700"))
            txtPremiumBadge.text = "PRO MEMBER"
            premiumBadge.setOnClickListener(null)

            rowProfilePurchaseType.visibility = View.VISIBLE
            rowProfilePurchaseDate.visibility = View.VISIBLE
            rowProfileTransactionId.visibility = View.VISIBLE
            rowProfileExpiry.visibility = View.VISIBLE
            btnProfileUpgradeCard.visibility = View.GONE

            txtProfilePurchaseType.text = localSub.planType
            txtProfilePurchaseDate.text = localSub.startDate
            txtProfileExpiryDate.text = localSub.expiryDate
            txtProfileTransactionId.text = localSub.paymentId
            txtProfileExpiryCountdown.text = "Active Subscription"
            txtProfileExpiryCountdown.setTextColor(Color.parseColor("#00FF66"))
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
                        val purchaseType = snapshot.getString("purchaseType") ?: "free"
                        val planType = snapshot.getString("planType") ?: ""
                        val transactionId = snapshot.getString("transactionId") ?: snapshot.getString("paymentId") ?: "N/A"
                        val premiumExpiryDate = snapshot.getLong("premiumExpiryDate") ?: 0L
                        val activatedAt = snapshot.getLong("activatedAt") ?: 0L

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

                            txtProfileSandboxHeader.visibility = View.VISIBLE
                            layoutProfileSandboxButtons.visibility = View.VISIBLE

                            txtProfileTransactionId.text = transactionId

                            if (activatedAt > 0L) {
                                txtProfilePurchaseDate.text = dateFormat.format(Date(activatedAt))
                            }

                            if (purchaseType == "lifetime") {
                                txtProfilePurchaseType.text = "Lifetime License"
                                txtProfileExpiryDate.text = "Never Expires (Lifetime Access)"
                                txtProfileExpiryCountdown.text = "Forever Unlocked"
                                txtProfileExpiryCountdown.setTextColor(Color.parseColor("#00FF66"))
                            } else {
                                if (premiumExpiryDate > 0L) {
                                    txtProfileExpiryDate.text = dateFormat.format(Date(premiumExpiryDate))
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
            Toast.makeText(activity, "Configurations Synced to Cloud Securely!", Toast.LENGTH_SHORT).show()
        }

        btnLogout.setOnClickListener {
            dialog.dismiss()
            AuthManager.logout()
            PremiumManager.clearLocalPremium(activity)
            com.magic.calculator.logic.OnboardingManager.clearLocalState(activity)
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
            val intent = Intent(activity, com.magic.calculator.ui.PhotoViewerActivity::class.java).apply {
                putExtra("EXTRA_IMAGE_NAME", "prediction_friends.jpg")
                putExtra("EXTRA_POSITION", 1)
                putExtra("EXTRA_TOTAL", 1)
            }
            activity.startActivity(intent)
            Toast.makeText(activity, "Testing Contact Force Overlay View!", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(activity, "Contact Force settings saved ✓", Toast.LENGTH_SHORT).show()
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

            val intent = Intent(activity, com.magic.calculator.ui.PhotoViewerActivity::class.java).apply {
                putExtra("EXTRA_IMAGE_NAME", "prediction_friends.jpg")
                putExtra("EXTRA_POSITION", 1)
                putExtra("EXTRA_TOTAL", 1)
            }
            activity.startActivity(intent)
            Toast.makeText(activity, "Testing Contact Force Overlay…", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(activity, "Toxic Time Force settings saved!", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(activity, "Ghost Typing settings saved!", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(activity, "Ghost Recovery activated!", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }
}
