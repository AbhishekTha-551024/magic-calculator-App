package com.magic.calculator.feature.vault.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.magic.calculator.di.appContainer
import com.magic.calculator.feature.vault.domain.model.VaultAlbum
import com.magic.calculator.feature.vault.domain.model.VaultMedia
import com.magic.calculator.feature.vault.domain.repository.VaultRepository
import com.magic.calculator.feature.vault.domain.usecase.GetVaultAlbumsUseCase
import com.magic.calculator.feature.vault.domain.usecase.GetVaultPhotosUseCase
import com.magic.calculator.feature.vault.domain.usecase.GetVaultThemeUseCase
import com.magic.calculator.feature.vault.domain.usecase.SetVaultThemeUseCase

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: VaultRepository = application.appContainer.vaultRepository
    private val getVaultPhotosUseCase = GetVaultPhotosUseCase(repository)
    private val getVaultAlbumsUseCase = GetVaultAlbumsUseCase(repository)
    private val getVaultThemeUseCase = GetVaultThemeUseCase(repository)
    private val setVaultThemeUseCase = SetVaultThemeUseCase(repository)

    private val _isDarkTheme = MutableLiveData<Boolean>()
    val isDarkTheme: LiveData<Boolean> = _isDarkTheme

    private val _photos = MutableLiveData<List<VaultMedia>>()
    val photos: LiveData<List<VaultMedia>> = _photos

    private val _albums = MutableLiveData<List<VaultAlbum>>()
    val albums: LiveData<List<VaultAlbum>> = _albums

    init {
        _isDarkTheme.value = getVaultThemeUseCase()
        loadPhotos()
        loadAlbums()
    }

    fun setTheme(isDark: Boolean) {
        setVaultThemeUseCase(isDark)
        _isDarkTheme.value = isDark
    }

    fun loadPhotos() {
        _photos.value = getVaultPhotosUseCase()
    }

    fun loadAlbums() {
        _albums.value = getVaultAlbumsUseCase()
    }
}
