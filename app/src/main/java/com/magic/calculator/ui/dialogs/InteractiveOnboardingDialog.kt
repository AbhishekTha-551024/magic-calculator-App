package com.magic.calculator.ui.dialogs

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.magic.calculator.R
import com.magic.calculator.logic.OnboardingManager
import com.magic.calculator.ui.util.MagicToast

/**
 * InteractiveOnboardingDialog
 *
 * Provides a step-by-step interactive onboarding masterclass for both:
 * 1. Free Magicians (New Users) — 4 Core Steps
 * 2. Pro Magicians (VIP Subscribers) — 5 Advanced Steps
 */
object InteractiveOnboardingDialog {

    data class OnboardingStep(
        val title: String,
        val description: String,
        val iconRes: Int,
        val accentColor: String,
        val badgeText: String,
        val hintHeader: String,
        val hintBody: String,
        val tryButtonText: String? = null,
        val tryAction: ((Activity) -> Unit)? = null
    )

    fun show(
        activity: Activity,
        isProTour: Boolean,
        onDismiss: (() -> Unit)? = null
    ) {
        if (activity.isFinishing || activity.isDestroyed) return

        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_interactive_onboarding)

        dialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0.88f)
        }

        val steps = if (isProTour) getProSteps() else getFreeSteps()
        var currentStepIndex = 0

        val tvBadge = dialog.findViewById<TextView>(R.id.tvTourBadge)
        val btnSkip = dialog.findViewById<TextView>(R.id.btnTourSkip)
        val imgHeroIcon = dialog.findViewById<ImageView>(R.id.imgTourHeroIcon)
        val tvTitle = dialog.findViewById<TextView>(R.id.tvTourTitle)
        val tvDesc = dialog.findViewById<TextView>(R.id.tvTourDescription)
        val tvHintHeader = dialog.findViewById<TextView>(R.id.tvTourHintHeader)
        val tvHintBody = dialog.findViewById<TextView>(R.id.tvTourHintBody)
        val btnTry = dialog.findViewById<Button>(R.id.btnTourTryAction)
        val layoutDots = dialog.findViewById<LinearLayout>(R.id.layoutTourDots)
        val btnPrevious = dialog.findViewById<Button>(R.id.btnTourPrevious)
        val btnNext = dialog.findViewById<Button>(R.id.btnTourNext)

        fun setupDots() {
            layoutDots.removeAllViews()
            for (i in steps.indices) {
                val dot = ImageView(activity).apply {
                    val size = if (i == currentStepIndex) 24 else 18
                    val layoutParams = LinearLayout.LayoutParams(size, 8).apply {
                        setMargins(6, 0, 6, 0)
                    }
                    this.layoutParams = layoutParams
                    setImageResource(if (i == currentStepIndex) R.drawable.dot_active else R.drawable.dot_inactive)
                }
                layoutDots.addView(dot)
            }
        }

        fun updateUI() {
            val step = steps[currentStepIndex]
            val accent = Color.parseColor(step.accentColor)

            tvBadge.text = step.badgeText
            imgHeroIcon.setImageResource(step.iconRes)
            imgHeroIcon.setColorFilter(accent)
            tvTitle.text = step.title
            tvDesc.text = step.description
            tvHintHeader.text = step.hintHeader
            tvHintHeader.setTextColor(accent)
            tvHintBody.text = step.hintBody

            if (step.tryButtonText != null && step.tryAction != null) {
                btnTry.visibility = View.VISIBLE
                btnTry.text = step.tryButtonText
                btnTry.backgroundTintList = android.content.res.ColorStateList.valueOf(accent)
                btnTry.setOnClickListener { step.tryAction.invoke(activity) }
            } else {
                btnTry.visibility = View.GONE
            }

            btnPrevious.visibility = if (currentStepIndex > 0) View.VISIBLE else View.INVISIBLE
            btnNext.text = if (currentStepIndex == steps.size - 1) "Finish & Start Performing 🪄" else "Next Step →"
            btnNext.backgroundTintList = android.content.res.ColorStateList.valueOf(accent)

            setupDots()
        }

        fun completeTour() {
            if (isProTour) {
                OnboardingManager.markProOnboardingCompleted(activity)
                MagicToast.show(activity, "Pro Masterclass Completed! All Advanced Magics Ready.")
            } else {
                OnboardingManager.markFreeOnboardingCompleted(activity)
                MagicToast.show(activity, "Magician Tour Completed! Ready to perform.")
            }
            dialog.dismiss()
            onDismiss?.invoke()
        }

        btnNext.setOnClickListener {
            if (currentStepIndex < steps.size - 1) {
                currentStepIndex++
                updateUI()
            } else {
                completeTour()
            }
        }

        btnPrevious.setOnClickListener {
            if (currentStepIndex > 0) {
                currentStepIndex--
                updateUI()
            }
        }

        btnSkip.visibility = if (isProTour) View.VISIBLE else View.GONE
        btnSkip.setOnClickListener {
            completeTour()
        }

        dialog.setCancelable(isProTour)
        updateUI()
        dialog.show()
    }

    private fun getFreeSteps(): List<OnboardingStep> {
        return listOf(
            OnboardingStep(
                title = "The Invisible Disguise",
                description = "To any spectator, this app appears as a completely innocent standard calculator that accurately computes everyday math.",
                iconRes = R.drawable.ic_magic,
                accentColor = "#00E5FF",
                badgeText = "MAGICIAN TOUR • 1 / 4",
                hintHeader = "💡 INNOCENT CALCULATOR LOOK",
                hintBody = "Let spectators hold the phone and type equations like 25 + 25 = 50 to establish 100% credibility.",
                tryButtonText = "✨ Test Display Feedback",
                tryAction = { act -> MagicToast.show(act, "Spectator mode active: Standard arithmetic verified!") }
            ),
            OnboardingStep(
                title = "Secret Master Backdoor (741=)",
                description = "Type your master PIN '741' and press '=' on the calculator (or Call on Dialer) to unlock your secret backstage control panel.",
                iconRes = R.drawable.ic_lock,
                accentColor = "#FFD700",
                badgeText = "MAGICIAN TOUR • 2 / 4",
                hintHeader = "🔐 COVERT BACKDOOR ENTRY",
                hintBody = "The backdoor PIN is 100% invisible to spectators. You can also customize your own 3-digit PIN in 'My Profile'.",
                tryButtonText = "🔑 Master PIN: 741",
                tryAction = { act -> MagicToast.show(act, "Master PIN '741=' will always unlock backstage control!") }
            ),
            OnboardingStep(
                title = "Core Number Forces",
                description = "Easily force numbers with Digital Flip (66 flips to 99), Lucky Number (777), or Phone Number Force.",
                iconRes = R.drawable.ic_theme,
                accentColor = "#FF2D55",
                badgeText = "MAGICIAN TOUR • 3 / 4",
                hintHeader = "🎲 MENTALISM NUMBER FORCES",
                hintBody = "Customize your forced phone number and trigger values under the 'Edit Parameters' tab.",
                tryButtonText = "📱 Practice Digital Flip",
                tryAction = { act -> MagicToast.show(act, "Flip trigger ready: Entering 66 flips to 99 on trigger!") }
            ),
            OnboardingStep(
                title = "Invisible Sensor Triggers",
                description = "Trigger revelations hands-free using your phone's built-in Proximity, Light Shadow, Tilt, or Magnetometer sensors.",
                iconRes = R.drawable.ic_settings,
                accentColor = "#00FF66",
                badgeText = "MAGICIAN TOUR • 4 / 4",
                hintHeader = "📡 SENSOR DIAGNOSTICS",
                hintBody = "Wave your hand over the top edge or tilt the phone slightly to secretly morph the calculated result!",
                tryButtonText = "🌊 Test Proximity Wave",
                tryAction = { act -> MagicToast.show(act, "Sensor armed: Wave your hand over the top speaker!") }
            )
        )
    }

    private fun getProSteps(): List<OnboardingStep> {
        return listOf(
            OnboardingStep(
                title = "Welcome to Pro Mentalism",
                description = "You have unlocked the full VIP suite of advanced hardware illusions, real-time wireless links, and stealth disguises.",
                iconRes = R.drawable.ic_magic,
                accentColor = "#FFD700",
                badgeText = "PRO MASTERCLASS • 1 / 5",
                hintHeader = "👑 VIP PRO ENTITLEMENTS ACTIVE",
                hintBody = "All 12 advanced strategies, stealth phone dialer, and dynamic gallery prediction engines are unlocked.",
                tryButtonText = "✨ Status: PRO MEMBER",
                tryAction = { act -> MagicToast.show(act, "VIP Status Verified: All Pro engines armed!") }
            ),
            OnboardingStep(
                title = "Toxic Time Force",
                description = "Force the calculated result to automatically match the spectator's exact live Clock Time (HH:MM), Date (DD/MM), or Battery %.",
                iconRes = R.drawable.ic_theme,
                accentColor = "#00E5FF",
                badgeText = "PRO MASTERCLASS • 2 / 5",
                hintHeader = "⏱️ REAL-TIME SYSTEM FORCE",
                hintBody = "Spectators can type any random math, yet pressing '=' yields their exact current watch time!",
                tryButtonText = "🕒 Try Time Force Demo",
                tryAction = { act -> MagicToast.show(act, "Toxic Time armed: Next equation forces live watch time!") }
            ),
            OnboardingStep(
                title = "Ghost Typing & Recovery",
                description = "Watch numbers type themselves automatically on screen with a custom delay, or vanish and reappear on sensor wave.",
                iconRes = R.drawable.ic_help,
                accentColor = "#A855F7",
                badgeText = "PRO MASTERCLASS • 3 / 5",
                hintHeader = "👻 PHANTOM TYPING ENGINE",
                hintBody = "Set your Ghost Digits and Delay in milliseconds inside the Ghost Typing modal configuration card.",
                tryButtonText = "👻 Trigger Ghost Pulse",
                tryAction = { act -> MagicToast.show(act, "Ghost Typing initialized with custom typing cadence!") }
            ),
            OnboardingStep(
                title = "P2P Online Magic Link",
                description = "Connect two Android devices wirelessly over Cloud Firestore for live keystroke mirroring and remote puppet triggers.",
                iconRes = R.drawable.ic_share,
                accentColor = "#00FF66",
                badgeText = "PRO MASTERCLASS • 4 / 5",
                hintHeader = "🌐 DUAL-DEVICE COGNITION",
                hintBody = "Your accomplice sees whatever digits the spectator types on their phone in real time from across the room.",
                tryButtonText = "🔗 Check Cloud Link",
                tryAction = { act -> MagicToast.show(act, "P2P Link: Cloud sync channel ready for dual-screen performance.") }
            ),
            OnboardingStep(
                title = "Stealth Gallery & MoiLock",
                description = "Transform the app into a full Photo Gallery with microscopic prediction stamps, or a realistic lock screen that extracts PINs.",
                iconRes = R.drawable.ic_lock,
                accentColor = "#FF9100",
                badgeText = "PRO MASTERCLASS • 5 / 5",
                hintHeader = "🎭 COMPLETE DISGUISE SUITE",
                hintBody = "Switch seamlessly between Magic Calculator, Phone Dialer, Photo Gallery, and MoiLock from the Theme tab.",
                tryButtonText = "🚀 Ready To Perform!",
                tryAction = { act -> MagicToast.show(act, "You're all set to astound audiences worldwide!") }
            )
        )
    }
}
