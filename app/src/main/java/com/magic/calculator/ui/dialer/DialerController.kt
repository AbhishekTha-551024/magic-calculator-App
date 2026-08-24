package com.magic.calculator.ui.dialer

import android.animation.ObjectAnimator
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.magic.calculator.R
import com.magic.calculator.SecretActivity
import com.magic.calculator.audio.SoundManager
import com.magic.calculator.databinding.LayoutDialerBinding
import com.magic.calculator.haptic.HapticManager
import com.magic.calculator.logic.MagicEngine
import com.magic.calculator.logic.PremiumManager
import com.magic.calculator.logic.SettingsManager
import com.magic.calculator.ui.CallLogAdapter
import com.magic.calculator.ui.CallLogEntry
import com.magic.calculator.ui.ContactAdapter
import com.magic.calculator.ui.ContactEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Controller encapsulating all Phone Dialer features, keypad actions,
 * contact detail overlays, and dialer magic execution.
 */
class DialerController(
    private val activity: AppCompatActivity,
    private val binding: LayoutDialerBinding,
    private val settingsManager: SettingsManager,
    private val soundManager: SoundManager,
    private val hapticManager: HapticManager,
    private val magicEngine: MagicEngine,
    private val onLuckyTrigger: () -> Unit
) {

    var dialInput: String = ""
        private set

    private var isMissedOnly = false

    fun setDialInput(text: String) {
        dialInput = text
        binding.tvDialNumber.text = text
    }

    fun init() {
        setupKeypadListeners()
        setupTabListeners()
        setupContactDetailsListeners()
    }

    private fun setupKeypadListeners() {
        val dialButtons = listOf(
            binding.dial0 to "0", binding.dial1 to "1", binding.dial2 to "2",
            binding.dial3 to "3", binding.dial4 to "4", binding.dial5 to "5",
            binding.dial6 to "6", binding.dial7 to "7", binding.dial8 to "8",
            binding.dial9 to "9", binding.dialStar to "*", binding.dialHash to "#"
        )

        for ((btn, digit) in dialButtons) {
            btn.setOnClickListener {
                dialInput += digit
                binding.tvDialNumber.text = dialInput
                soundManager.playButtonTick()
                hapticManager.buttonTick()
            }
        }

        binding.btnDialBackspace.setOnClickListener {
            if (dialInput.isNotEmpty()) {
                dialInput = dialInput.dropLast(1)
                binding.tvDialNumber.text = dialInput
                soundManager.playButtonTick()
                hapticManager.buttonTick()
            }
        }

        binding.btnDialBackspace.setOnLongClickListener {
            dialInput = ""
            binding.tvDialNumber.text = ""
            hapticManager.buttonTick()
            soundManager.playButtonTick()
            true
        }

        binding.btnCall.setOnClickListener {
            soundManager.playButtonTick()
            hapticManager.buttonTick()
            handleCallAction()
        }

        binding.fabAction.setOnClickListener {
            binding.dialPadOverlay.visibility = View.VISIBLE
        }

        binding.vDimmer.setOnClickListener {
            binding.dialPadOverlay.visibility = View.GONE
        }
    }

    private fun setupTabListeners() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            switchDialerTab(item.itemId)
            true
        }

        binding.tabAll.setOnClickListener {
            isMissedOnly = false
            updateSubTabUI()
            loadCallLogs()
        }

        binding.tabMissed.setOnClickListener {
            isMissedOnly = true
            updateSubTabUI()
            loadCallLogs()
        }
    }

    private fun setupContactDetailsListeners() {
        binding.btnDetailsBack.setOnClickListener {
            binding.layoutContactDetails.visibility = View.GONE
        }

        // 🎩 Video logo को 5 seconds hold करो → Gallery में Contact Force prediction खुलेगी
        setupVideoButtonMagicHold()
    }

    /**
     * Video button पर 5 second silent hold:
     * - ACTION_DOWN पर countdown शुरू होता है (coroutine Job)
     * - 5s पूरे होने पर Gallery खुलती है prediction_friends.jpg के साथ
     * - अगर finger उठा ली (ACTION_UP/CANCEL) तो countdown cancel
     * - दर्शक को कुछ पता नहीं चलता — बिल्कुल normal Video button लगती है
     */
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private fun setupVideoButtonMagicHold() {
        val HOLD_DURATION_MS = 2000L
        var holdJob: Job? = null

        binding.btnDetailsVideo.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    holdJob?.cancel()
                    holdJob = activity.lifecycleScope.launch {
                        delay(HOLD_DURATION_MS)
                        withContext(Dispatchers.Main) {
                            // ✅ 5 seconds पूरे — Gallery खोलो!
                            openGalleryWithContactForce()
                        }
                    }
                    false // normal click भी काम करे
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    holdJob?.cancel()
                    holdJob = null
                    false
                }
                else -> false
            }
        }
    }

    /**
     * App को Gallery mode में switch करके prediction_friends.jpg खोलता है।
     * forcedContactName और forcedContactPhone पहले से
     * showContactDetailScreen() में save हो चुके होते हैं।
     */
    private fun openGalleryWithContactForce() {
        // Gallery mode set करो (appMode = 3)
        settingsManager.appMode = 3

        // Fallback: अगर नाम/नंबर किसी वजह से खाली हो
        if (settingsManager.forcedContactName.isEmpty()) {
            settingsManager.forcedContactName = binding.tvDetailsName.text.toString()
        }
        if (settingsManager.forcedContactPhone.isEmpty()) {
            settingsManager.forcedContactPhone = binding.tvDetailsPhone.text.toString()
        }

        // prediction_friends.jpg सीधे full-screen में खोलो
        val intent = Intent(activity, com.magic.calculator.ui.PhotoViewerActivity::class.java).apply {
            putExtra("EXTRA_IMAGE_NAME", "prediction_friends.jpg")
            putExtra("EXTRA_POSITION", 1)
            putExtra("EXTRA_TOTAL", 1)
            putExtra("EXTRA_HIDE_BARS_INITIALLY", true)
        }
        activity.startActivity(intent)
        activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        activity.finish()
    }

    private fun handleCallAction() {
        val input = dialInput.trim()
        val customPin = settingsManager.secretUnlockCode.trim()

        // 1. Primary Permanent PIN ("741") OR Secondary Custom PIN (e.g. "456", "258")
        if (input == "741" || (customPin.isNotEmpty() && input == customPin)) {
            activity.startActivity(Intent(activity, SecretActivity::class.java))
            dialInput = ""
            binding.tvDialNumber.text = ""
            binding.dialPadOverlay.visibility = View.GONE
            return
        }

        when (input) {
            settingsManager.customFlipTrigger.trim() -> performDialerMagic(MagicEngine.TrickType.DIGIT_FLIP)
            "777" -> {
                if (settingsManager.activeMagic == "LUCKY") {
                    onLuckyTrigger()
                } else {
                    activity.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$input")))
                }
            }
            else -> {
                if (input.isNotEmpty()) {
                    activity.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$input")))
                }
            }
        }
    }

    fun performDialerMagic(type: MagicEngine.TrickType) {
        val result = magicEngine.performMagic(
            input = dialInput,
            type = type,
            forcedPhone = settingsManager.forcedPhoneNumber,
            forcedLucky = settingsManager.forcedLuckyNumber,
            flipResult  = settingsManager.customFlipResult
        )
        val animator = ObjectAnimator.ofFloat(binding.tvDialNumber, "rotationX", 0f, 360f)
        animator.duration = 600
        animator.interpolator = AccelerateDecelerateInterpolator()
        animator.addUpdateListener {
            if (animator.animatedFraction > 0.5f) {
                binding.tvDialNumber.text = result.displayValue
                dialInput = result.displayValue
            }
        }
        animator.start()
        hapticManager.magicVibrate()
        soundManager.playMagicSwoosh()
    }

    fun switchDialerTab(itemId: Int) {
        binding.rvCallLog.visibility  = View.GONE
        binding.rvContacts.visibility = View.GONE
        binding.dialerHeader.visibility = View.VISIBLE
        binding.fabAction.visibility    = View.GONE
        binding.fabAddContact.visibility = View.GONE

        when (itemId) {
            R.id.nav_dial -> {
                binding.rvCallLog.visibility  = View.VISIBLE
                binding.fabAction.visibility  = View.VISIBLE
                binding.subTabs.visibility    = View.VISIBLE
                binding.tvMainTitle.text      = "Dial"
                loadCallLogs()
            }
            R.id.nav_contacts -> {
                binding.rvContacts.visibility    = View.VISIBLE
                binding.fabAddContact.visibility = View.VISIBLE
                binding.subTabs.visibility       = View.GONE
                binding.tvMainTitle.text         = "Contacts"
                loadContacts()
            }
        }
    }

    fun loadCallLogs() {
        activity.lifecycleScope.launch(Dispatchers.IO) {
            val list = mutableListOf<CallLogEntry>()
            try {
                val cursor = activity.contentResolver.query(
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
                        list.add(
                            CallLogEntry(
                            it.getString(nameIdx) ?: "", it.getString(numIdx) ?: "",
                            typeStr, dateFormat.format(Date(it.getLong(dateIdx))))
                        )
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
                binding.rvCallLog.layoutManager = LinearLayoutManager(activity)
                binding.rvCallLog.adapter = CallLogAdapter(list) { entry ->
                    showContactDetailScreen(entry.name, entry.number)
                }
            }
        }
    }

    fun loadContacts() {
        activity.lifecycleScope.launch(Dispatchers.IO) {
            val list = mutableListOf<ContactEntry>()
            val injectedList = listOf(
                ContactEntry("Mom",    "+91XXXXXXXXXX", "Mobile Telephony"),
                ContactEntry("Friend", "+91XXXXXXXXXX", "Mobile Telephony"),
                ContactEntry("Boss",   "+91XXXXXXXXXX", "Mobile Telephony"),
                ContactEntry("Sister", "+91XXXXXXXXXX", "Mobile Telephony"),
                ContactEntry("Uncle",  "+91XXXXXXXXXX", "Mobile Telephony")
            )

            try {
                val cursor = activity.contentResolver.query(
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
                binding.rvContacts.layoutManager = LinearLayoutManager(activity)
                binding.rvContacts.adapter = ContactAdapter(list) { contact ->
                    showContactDetailScreen(contact.name, contact.number)
                }
            }
        }
    }

    fun showContactDetailScreen(name: String, phone: String) {
        if (settingsManager.isContactForceEnabled) {
            if (!PremiumManager.isUserPremium) {
                settingsManager.isContactForceEnabled = false
            } else {
                settingsManager.forcedContactName = if (name.isEmpty()) phone else name
                settingsManager.forcedContactPhone = phone
                settingsManager.activeMagic = "CONTACT_FORCE"
            }
        }

        activity.runOnUiThread {
            binding.tvDetailsName.text = if (name.isEmpty()) "Unknown" else name
            binding.tvDetailsPhone.text = phone

            val cities = listOf("Mumbai", "Delhi", "Maharashtra", "Kolkata", "Gujarat", "Karnataka", "Punjab")
            val carriers = listOf("Jio Mobile", "Airtel cellular", "Vodafone Idea", "BSNL Telephony")
            val city = cities[abs(name.hashCode()) % cities.size]
            val carrier = carriers[abs(phone.hashCode()) % carriers.size]
            binding.tvDetailsCarrier.text = "$city, $carrier"

            binding.cvDetailsAvatar.setCardBackgroundColor(Color.parseColor("#3B82F6"))
            binding.layoutContactDetails.visibility = View.VISIBLE
        }
    }

    private fun updateSubTabUI() {
        val isDark = settingsManager.isDialerThemeDark
        val primaryColor = if (isDark) Color.WHITE else Color.parseColor("#212121")
        val secondaryColor = if (isDark) Color.parseColor("#BBBBBB") else Color.parseColor("#757575")

        if (isMissedOnly) {
            binding.tvTabAll.setTextColor(secondaryColor)
            binding.indicatorAll.visibility = View.INVISIBLE
            binding.tvTabMissed.setTextColor(primaryColor)
            binding.indicatorMissed.visibility = View.VISIBLE
        } else {
            binding.tvTabAll.setTextColor(primaryColor)
            binding.indicatorAll.visibility = View.VISIBLE
            binding.tvTabMissed.setTextColor(secondaryColor)
            binding.indicatorMissed.visibility = View.INVISIBLE
        }
    }
}
