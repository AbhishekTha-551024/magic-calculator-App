package com.magic.calculator.logic

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.magic.calculator.R

/**
 * AppIconManager
 *
 * Dynamically switches launcher home-screen app icon and title using Android Activity Aliases:
 * - Calculator
 * - Calendar
 * - Notes
 * - Settings
 * - Messages
 */
object AppIconManager {

    enum class AppIconType(
        val key: String,
        val aliasClass: String,
        val title: String,
        val subtitle: String,
        val iconRes: Int
    ) {
        CALCULATOR(
            "CALCULATOR",
            "com.magic.calculator.SplashCalculator",
            "Calculator",
            "Default stealth calculator emblem",
            R.drawable.ic_app_icon_calculator
        ),
        CALENDAR(
            "CALENDAR",
            "com.magic.calculator.SplashCalendar",
            "Calendar",
            "Disguised as Calendar application",
            R.drawable.ic_app_icon_calendar
        ),
        NOTES(
            "NOTES",
            "com.magic.calculator.SplashNotes",
            "Notes",
            "Disguised as Yellow Notepad",
            R.drawable.ic_app_icon_notes
        ),
        SETTINGS(
            "SETTINGS",
            "com.magic.calculator.SplashSettings",
            "Settings",
            "Disguised as System Settings",
            R.drawable.ic_app_icon_settings
        ),
        MESSAGES(
            "MESSAGES",
            "com.magic.calculator.SplashMessages",
            "Messages",
            "Disguised as Green SMS Messenger",
            R.drawable.ic_app_icon_messages
        );

        companion object {
            fun fromKey(key: String): AppIconType {
                return values().firstOrNull { it.key.equals(key, ignoreCase = true) } ?: CALCULATOR
            }
        }
    }

    private const val PREFS_NAME = "MagicAppIconPrefs"
    private const val KEY_SELECTED_ICON = "selected_app_icon"

    fun getActiveAppIcon(context: Context): AppIconType {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = prefs.getString(KEY_SELECTED_ICON, AppIconType.CALCULATOR.key) ?: AppIconType.CALCULATOR.key
        return AppIconType.fromKey(key)
    }

    fun setAppIcon(context: Context, targetIcon: AppIconType): Boolean {
        try {
            val pm = context.packageManager
            val pkg = context.packageName

            for (icon in AppIconType.values()) {
                val component = ComponentName(pkg, icon.aliasClass)
                val newState = if (icon == targetIcon) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                }

                pm.setComponentEnabledSetting(
                    component,
                    newState,
                    PackageManager.DONT_KILL_APP
                )
            }

            // Save in prefs
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_SELECTED_ICON, targetIcon.key)
                .apply()

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}
