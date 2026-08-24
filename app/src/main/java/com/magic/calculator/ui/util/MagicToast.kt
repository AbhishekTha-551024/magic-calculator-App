package com.magic.calculator.ui.util

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.magic.calculator.R

/**
 * MagicToast
 * Displays a premium custom floating toast with the app's Magic Logo on all Android versions.
 */
object MagicToast {

    fun show(context: Context?, message: String?, isLong: Boolean = false) {
        if (context == null || message.isNullOrBlank()) return

        val duration = if (isLong) 3500L else 2000L

        // Attempt to unwrap Activity from context
        val activity = when (context) {
            is Activity -> context
            is android.content.ContextWrapper -> {
                var c: Context? = context
                while (c is android.content.ContextWrapper) {
                    if (c is Activity) break
                    c = c.baseContext
                }
                c as? Activity
            }
            else -> null
        }

        if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
            activity.runOnUiThread {
                try {
                    val decorView = activity.window.decorView as? ViewGroup ?: return@runOnUiThread
                    val inflater = LayoutInflater.from(activity)
                    val toastView = inflater.inflate(R.layout.layout_custom_magic_toast, decorView, false)

                    toastView.findViewById<TextView>(R.id.tvToastMessage)?.text = message
                    toastView.findViewById<ImageView>(R.id.imgToastLogo)?.setImageResource(R.drawable.ic_magic)

                    val bottomMarginPx = (decorView.height * 0.12).toInt().coerceAtLeast(160)
                    val layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                        bottomMargin = bottomMarginPx
                    }

                    toastView.layoutParams = layoutParams
                    toastView.alpha = 0f
                    toastView.translationY = 40f
                    decorView.addView(toastView)

                    // Smooth fade-in & slide-up animation
                    toastView.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(220)
                        .start()

                    // Auto fade-out & remove
                    Handler(Looper.getMainLooper()).postDelayed({
                        toastView.animate()
                            .alpha(0f)
                            .translationY(20f)
                            .setDuration(220)
                            .withEndAction {
                                decorView.removeView(toastView)
                            }.start()
                    }, duration)
                    return@runOnUiThread
                } catch (e: Exception) {
                    // Fallback below
                }
            }
            return
        }

        // Fallback for non-Activity / background contexts
        Handler(Looper.getMainLooper()).post {
            try {
                Toast.makeText(context, "🪄 $message", if (isLong) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun showLong(context: Context?, message: String?) {
        show(context, message, isLong = true)
    }
}
