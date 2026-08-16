package com.magic.calculator

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

class MagicApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)

        val firebaseAppCheck = FirebaseAppCheck.getInstance()
        if (BuildConfig.DEBUG) {
            firebaseAppCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
            Log.d("MagicApplication", "Installed DebugAppCheckProviderFactory")

            // Force token request to trigger DebugAppCheckProvider logcat print
            firebaseAppCheck.getAppCheckToken(true).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.i("FIREBASE_APP_CHECK", "App Check Token obtained successfully: ${task.result?.token}")
                } else {
                    Log.w("FIREBASE_APP_CHECK", "App Check Token task result: ${task.exception?.message}")
                }
            }
        } else {
            firebaseAppCheck.installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance()
            )
        }
    }
}
