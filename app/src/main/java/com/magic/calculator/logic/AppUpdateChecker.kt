package com.magic.calculator.logic

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.magic.calculator.BuildConfig

/**
 * AppUpdateChecker
 *
 * Fetches `app_config/version_control` from Firestore on launch and shows
 * an update dialog if a newer version is available.
 *
 * Firestore document structure:
 *   app_config/version_control:
 *     latestVersionCode  (Long)    — e.g. 2
 *     apkUrl             (String)  — direct APK download or Play Store link
 *     isMandatory        (Boolean) — if true, user cannot dismiss dialog
 *     updateMessage      (String)  — shown in the dialog body
 */
object AppUpdateChecker {

    private const val TAG = "AppUpdateChecker"
    private const val COLLECTION = "app_config"
    private const val DOCUMENT   = "version_control"

    /**
     * Call this on app launch (LoginActivity / MainActivity).
     *
     * @param context   Activity context (needed to show AlertDialog)
     * @param onNoUpdate  Called when no update is needed — proceed into app normally.
     *                    Also called on any Firestore error so the app is never blocked.
     */
    fun checkForUpdate(context: Context, onNoUpdate: () -> Unit) {
        FirebaseFirestore.getInstance()
            .collection(COLLECTION)
            .document(DOCUMENT)
            .get()
            .addOnSuccessListener { doc ->
                if (doc == null || !doc.exists()) {
                    Log.d(TAG, "No version_control document found — skipping update check.")
                    onNoUpdate()
                    return@addOnSuccessListener
                }

                val latestVersionCode = doc.getLong("latestVersionCode") ?: 0L
                val apkUrl            = doc.getString("apkUrl") ?: ""
                val isMandatory       = doc.getBoolean("isMandatory") ?: false
                val updateMessage     = doc.getString("updateMessage")
                    ?: "A new version of Magic Calculator is available. Please update to continue."

                val currentVersionCode = BuildConfig.VERSION_CODE.toLong()

                Log.d(TAG, "Current: $currentVersionCode | Latest: $latestVersionCode | Mandatory: $isMandatory")

                if (latestVersionCode > currentVersionCode) {
                    // New version available — show dialog on UI thread
                    (context as? android.app.Activity)?.runOnUiThread {
                        showUpdateDialog(context, apkUrl, isMandatory, updateMessage, onNoUpdate)
                    } ?: showUpdateDialog(context, apkUrl, isMandatory, updateMessage, onNoUpdate)
                } else {
                    // App is up to date
                    onNoUpdate()
                }
            }
            .addOnFailureListener { e ->
                // Network failure or Firestore error — silently log and proceed
                Log.w(TAG, "Update check failed (network/Firestore error) — proceeding normally: ${e.message}")
                onNoUpdate()
            }
    }

    private fun showUpdateDialog(
        context: Context,
        apkUrl: String,
        isMandatory: Boolean,
        updateMessage: String,
        onNoUpdate: () -> Unit
    ) {
        val builder = AlertDialog.Builder(context, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("🚀 New Update Available")
            .setMessage(updateMessage)
            .setCancelable(!isMandatory)
            .setPositiveButton("Update Now") { _, _ ->
                openUrl(context, apkUrl)
                // If mandatory, close the activity so user cannot continue
                if (isMandatory) {
                    (context as? android.app.Activity)?.finish()
                }
            }

        // Only show "Later" button if update is optional
        if (!isMandatory) {
            builder.setNegativeButton("Later") { dialog, _ ->
                dialog.dismiss()
                onNoUpdate()
            }
        }

        val dialog = builder.create()

        // Prevent dismissal by tapping outside for mandatory updates
        if (isMandatory) {
            dialog.setCanceledOnTouchOutside(false)
        }

        try {
            dialog.show()

            // Style the positive button
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
                setTextColor(android.graphics.Color.parseColor("#00E5FF"))
            }
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.apply {
                setTextColor(android.graphics.Color.parseColor("#A0A0A5"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show update dialog: ${e.message}")
            onNoUpdate()
        }
    }

    private fun openUrl(context: Context, rawUrl: String) {
        val url = rawUrl.trim()
        if (url.isBlank()) {
            Log.w(TAG, "APK URL is empty — cannot open update link.")
            return
        }
        try {
            val formattedUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
                "https://$url"
            } else {
                url
            }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(formattedUrl))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open APK URL: $url — ${e.message}")
        }
    }
}
