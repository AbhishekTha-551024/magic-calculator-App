package com.magic.calculator.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowInsetsController
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.magic.calculator.R
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.magic.calculator.viewmodel.GalleryViewModel

class GalleryActivity : AppCompatActivity() {

    private lateinit var viewModel: GalleryViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)
        
        viewModel = ViewModelProvider(this)[GalleryViewModel::class.java]

        val container = findViewById<android.view.View>(R.id.gallery_container)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        viewModel.isDarkTheme.observe(this) { isDark ->
            val bgColor = if (isDark) Color.BLACK else Color.WHITE
            val textColor = if (isDark) Color.WHITE else Color.parseColor("#1F1F1F")
            
            container.setBackgroundColor(bgColor)
            bottomNav.setBackgroundColor(bgColor)
            
            val colorStateList = ColorStateList.valueOf(textColor)
            bottomNav.itemIconTintList = colorStateList
            bottomNav.itemTextColor = colorStateList

            // Edge to Edge setup
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val controller = window.insetsController
                if (controller != null) {
                    val appearance = if (isDark) 0 else WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                    controller.setSystemBarsAppearance(
                        appearance,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                    )
                }
            } else {
                val controller = WindowInsetsControllerCompat(window, window.decorView)
                controller.isAppearanceLightStatusBars = !isDark
                controller.isAppearanceLightNavigationBars = !isDark
            }
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_photos -> loadFragment(PhotosFragment())
                R.id.nav_albums -> loadFragment(AlbumsFragment())
                R.id.nav_recommended -> loadFragment(RecommendedFragment())
                R.id.nav_search -> loadFragment(SearchFragment())
            }
            true
        }

        // Load default fragment
        if (savedInstanceState == null) {
            bottomNav.selectedItemId = R.id.nav_photos
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.nav_host_fragment, fragment)
            .commit()
    }
}
