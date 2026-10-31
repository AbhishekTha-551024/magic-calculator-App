package com.magic.calculator.core.audio

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * One place that answers "may we record?".
 *
 * `checkSelfPermission(RECORD_AUDIO) == PERMISSION_GRANTED` was written out inline in ten-plus
 * places, each free to interpret the result differently. [REQUEST_CODE] is the single permission
 * request code for the microphone across the app.
 */
object MicPermission {

    const val REQUEST_CODE = 200

    fun has(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Returns true when the microphone may be used. When it may not, asks the user — unless
     * [rationaleShownOnly] is set, in which case the caller keeps its own explanatory UI.
     */
    fun ensure(activity: Activity, rationaleShownOnly: Boolean = false): Boolean {
        if (has(activity)) return true
        if (!rationaleShownOnly) {
            ActivityCompat.requestPermissions(
                activity, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_CODE
            )
        }
        return false
    }
}
