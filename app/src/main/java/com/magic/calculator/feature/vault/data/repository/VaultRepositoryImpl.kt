package com.magic.calculator.feature.vault.data.repository

import com.magic.calculator.feature.vault.data.datasource.VaultLocalDataSource
import com.magic.calculator.feature.vault.domain.model.VaultAlbum
import com.magic.calculator.feature.vault.domain.model.VaultMedia
import com.magic.calculator.feature.vault.domain.repository.VaultRepository

class VaultRepositoryImpl(
    private val localDataSource: VaultLocalDataSource
) : VaultRepository {

    override fun getVaultPhotos(): List<VaultMedia> {
        val rawFiles = localDataSource.listGalleryAssets().filter {
            it != "prediction_board.png" && it != "prediction_friends.jpg"
        }

        val result = mutableListOf<VaultMedia>()
        val activeMagic = localDataSource.getActiveMagic()

        if (activeMagic == "TOXIC_GALLERY") {
            result.add(
                VaultMedia(
                    fileName = "prediction_board.png",
                    isPrediction = true,
                    predictionType = VaultMedia.PredictionType.BOARD
                )
            )
        } else if (activeMagic == "CONTACT_FORCE" || activeMagic == "PHONE_NUMBER_FORCE") {
            result.add(
                VaultMedia(
                    fileName = "prediction_friends.jpg",
                    isPrediction = true,
                    predictionType = VaultMedia.PredictionType.FRIENDS
                )
            )
        }

        result.addAll(
            rawFiles.map {
                VaultMedia(
                    fileName = it,
                    isPrediction = false,
                    predictionType = VaultMedia.PredictionType.NONE
                )
            }
        )

        return result
    }

    override fun getVaultAlbums(): List<VaultAlbum> {
        val photos = localDataSource.listGalleryAssets()
        val total = photos.size
        val whatsapp = maxOf(1, (total * 0.3).toInt())
        val camera = maxOf(1, (total * 0.4).toInt())
        val instagram = maxOf(1, (total * 0.15).toInt())
        val screenshots = maxOf(1, total - whatsapp - camera - instagram)

        val firstPhoto = photos.firstOrNull()

        return listOf(
            VaultAlbum("All Photos", total, firstPhoto),
            VaultAlbum("WhatsApp Images", whatsapp, photos.getOrNull(1) ?: firstPhoto),
            VaultAlbum("Camera", camera, photos.getOrNull(2) ?: firstPhoto),
            VaultAlbum("Instagram", instagram, photos.getOrNull(3) ?: firstPhoto),
            VaultAlbum("Screenshots", screenshots, photos.getOrNull(4) ?: firstPhoto)
        )
    }

    override fun isDarkTheme(): Boolean = localDataSource.isDarkTheme()

    override fun setDarkTheme(isDark: Boolean) = localDataSource.setDarkTheme(isDark)
}
