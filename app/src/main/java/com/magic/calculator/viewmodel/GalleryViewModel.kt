package com.magic.calculator.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.magic.calculator.logic.SettingsManager

class GalleryViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsManager = SettingsManager(application)

    private val _isDarkTheme = MutableLiveData<Boolean>()
    val isDarkTheme: LiveData<Boolean> = _isDarkTheme

    init {
        _isDarkTheme.value = settingsManager.isGalleryThemeDark
    }

    fun setTheme(isDark: Boolean) {
        settingsManager.isGalleryThemeDark = isDark
        _isDarkTheme.value = isDark
    }
}
