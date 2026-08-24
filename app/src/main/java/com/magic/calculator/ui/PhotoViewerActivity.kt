package com.magic.calculator.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsetsController
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.magic.calculator.MainActivity
import com.magic.calculator.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class PhotoViewerActivity : AppCompatActivity() {

    private val imageList = mutableListOf<String>()
    private lateinit var adapter: androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>
    private lateinit var viewPager: androidx.viewpager2.widget.ViewPager2
    private lateinit var tvCount: TextView
    private val imageRotations = mutableMapOf<String, Float>()
    private val imageFilters = mutableMapOf<String, String>() // "original", "grayscale", "sepia", "invert"
    private val favoriteImages = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo_viewer)

        // Edge to Edge setup
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val controller = window.insetsController
            if (controller != null) {
                controller.setSystemBarsAppearance(
                    0,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                )
            }
        } else {
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
        }

        // --- PURE FULL SCREEN ENFORCEMENT ---
        if (intent.getBooleanExtra("EXTRA_HIDE_BARS_INITIALLY", false)) {
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            isBarsVisible = false
        }

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isTaskRoot) {
                    startActivity(Intent(this@PhotoViewerActivity, GalleryActivity::class.java))
                }
                finish()
            }
        })

        val initialPosition = intent.getIntExtra("EXTRA_POSITION", 1)
        
        // Load all image names
        val settingsManager = com.magic.calculator.logic.SettingsManager(this)
        val loadedImages = try {
            assets.list("gallery")?.filter { 
                val lower = it.lowercase()
                val isImage = lower.endsWith(".jpg") || lower.endsWith(".png") || lower.endsWith(".jpeg") || lower.endsWith(".webp")
                isImage && it != "prediction_board.png" && it != "prediction_friends.jpg"
            }?.sorted() ?: listOf()
        } catch (e: Exception) {
            listOf()
        }

        // Include custom prediction files in active modes
        val finalMutableList = loadedImages.toMutableList()
        if (settingsManager.activeMagic == "TOXIC_GALLERY") {
            finalMutableList.add(0, "prediction_board.png")
        } else if (settingsManager.activeMagic == "CONTACT_FORCE" || settingsManager.activeMagic == "PHONE_NUMBER_FORCE") {
            finalMutableList.add(0, "prediction_friends.jpg")
        }
        imageList.clear()
        imageList.addAll(finalMutableList)

        tvCount = findViewById<TextView>(R.id.tvCount)
        viewPager = findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.viewPager)

        adapter = object : androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): androidx.recyclerview.widget.RecyclerView.ViewHolder {
                val v = android.view.LayoutInflater.from(parent.context).inflate(R.layout.item_photo_full, parent, false)
                return object : androidx.recyclerview.widget.RecyclerView.ViewHolder(v) {}
            }

            override fun onBindViewHolder(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder, position: Int) {
                val pv = holder.itemView.findViewById<com.github.chrisbanes.photoview.PhotoView>(R.id.photoView)
                val fileName = imageList[position]
                
                try {
                    if (fileName == "prediction_board.png") {
                        val bitmap = com.magic.calculator.utils.PredictionOverlay.getOverlayBitmap(
                            this@PhotoViewerActivity, 
                            settingsManager.forcedGalleryTime, 
                            sampleSize = 1
                        )
                        pv.setImageBitmap(bitmap)
                    } else if (fileName == "prediction_friends.jpg") {
                        val bitmap = com.magic.calculator.utils.PredictionFriendsOverlay.getFriendsOverlayBitmap(
                            this@PhotoViewerActivity,
                            settingsManager.forcedContactName,
                            settingsManager.forcedContactPhone,
                            sampleSize = 1
                        )
                        if (bitmap != null) {
                            pv.setImageBitmap(bitmap)
                        } else {
                            val inputStream = assets.open("gallery/$fileName")
                            val options = BitmapFactory.Options().apply { inSampleSize = 1 }
                            val rawBitmap = BitmapFactory.decodeStream(inputStream, null, options)
                            pv.setImageBitmap(rawBitmap)
                            inputStream.close()
                        }
                    } else {
                        val inputStream = assets.open("gallery/$fileName")
                        val options = BitmapFactory.Options().apply {
                            inSampleSize = 1
                        }
                        val bitmap = BitmapFactory.decodeStream(inputStream, null, options)
                        pv.setImageBitmap(bitmap)
                        inputStream.close()
                    }
                    
                    // Apply rotation
                    pv.rotation = imageRotations[fileName] ?: 0f

                    // Apply filter
                    val currentFilter = imageFilters[fileName] ?: "original"
                    when (currentFilter) {
                        "grayscale" -> {
                            val matrix = android.graphics.ColorMatrix().apply { setSaturation(0f) }
                            pv.colorFilter = android.graphics.ColorMatrixColorFilter(matrix)
                        }
                        "sepia" -> {
                            val matrix = android.graphics.ColorMatrix().apply {
                                setSaturation(0f)
                                val sepia = android.graphics.ColorMatrix().apply { setScale(1f, 0.95f, 0.82f, 1.0f) }
                                postConcat(sepia)
                            }
                            pv.colorFilter = android.graphics.ColorMatrixColorFilter(matrix)
                        }
                        "invert" -> {
                            val matrix = android.graphics.ColorMatrix(floatArrayOf(
                                -1.0f, 0f, 0f, 0f, 255f,
                                0f, -1.0f, 0f, 0f, 255f,
                                0f, 0f, -1.0f, 0f, 255f,
                                0f, 0f, 0f, 1.0f, 0f
                            ))
                            pv.colorFilter = android.graphics.ColorMatrixColorFilter(matrix)
                        }
                        else -> {
                            pv.colorFilter = null
                        }
                    }

                    pv.setOnPhotoTapListener { _, _, _ ->
                        (this@PhotoViewerActivity).toggleBars()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override fun getItemCount() = imageList.size
        }

        viewPager.adapter = adapter
        viewPager.setCurrentItem(initialPosition - 1, false)
        tvCount.text = "$initialPosition/${imageList.size}"

        viewPager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                tvCount.text = "${position + 1}/${imageList.size}"
                updateHeartState()
            }
        })

        findViewById<ImageView>(R.id.ivBack).setOnClickListener {
            onBackPressed()
        }

        // DYNAMIC METADATA SYSTEM TRIGGER (Info Icon click)
        findViewById<ImageView>(R.id.ivInfo).setOnClickListener {
            val currentPos = viewPager.currentItem
            if (currentPos in imageList.indices) {
                showMetadataDialog(imageList[currentPos], currentPos + 1)
            }
        }

        // HEART / FAVORITE TOGGLE
        val ivHeart = findViewById<ImageView>(R.id.ivHeart)
        ivHeart.setOnClickListener {
            val currentPos = viewPager.currentItem
            if (currentPos in imageList.indices) {
                val fileName = imageList[currentPos]
                if (favoriteImages.contains(fileName)) {
                    favoriteImages.remove(fileName)
                    Toast.makeText(this, "Removed from Favorites", Toast.LENGTH_SHORT).show()
                } else {
                    favoriteImages.add(fileName)
                    Toast.makeText(this, "Added to Favorites", Toast.LENGTH_SHORT).show()
                }
                updateHeartState()
            }
        }

        // DISMISS METADATA DIALOG ON BACKDROP TAP
        findViewById<View>(R.id.metadataContainer).setOnClickListener {
            it.visibility = View.GONE
        }

        // SECRET TRIGGER (TvCount Hidden escape route back to settings)
        findViewById<TextView>(R.id.tvCount).setOnClickListener {
            startActivity(Intent(this, com.magic.calculator.SecretActivity::class.java))
            finishAffinity()
        }
        
        // Hide bars initially if requested
        if (intent.getBooleanExtra("EXTRA_HIDE_BARS_INITIALLY", false)) {
            findViewById<android.view.View>(R.id.topBar).visibility = android.view.View.GONE
            findViewById<android.view.View>(R.id.bottomBar).visibility = android.view.View.GONE
            findViewById<android.view.View>(R.id.tvRaw).visibility = android.view.View.GONE
        }

        // BIND BOTTOM ACTIONS
        findViewById<View>(R.id.btnShare).setOnClickListener {
            shareImage()
        }

        findViewById<View>(R.id.btnEdit).setOnClickListener {
            showFilterDialog()
        }

        findViewById<View>(R.id.btnDelete).setOnClickListener {
            confirmDelete()
        }

        findViewById<View>(R.id.btnMore).setOnClickListener {
            showMoreMenu()
        }

        updateHeartState()
    }

    private fun updateHeartState() {
        val currentPos = viewPager.currentItem
        if (currentPos in imageList.indices) {
            val fileName = imageList[currentPos]
            val ivHeart = findViewById<ImageView>(R.id.ivHeart)
            if (favoriteImages.contains(fileName)) {
                ivHeart.setColorFilter(Color.parseColor("#FF3B30")) // Red for favorited
            } else {
                ivHeart.setColorFilter(Color.WHITE)
            }
        }
    }

    private fun shareImage() {
        val currentPos = viewPager.currentItem
        if (currentPos !in imageList.indices) return
        val fileName = imageList[currentPos]
        val settingsManager = com.magic.calculator.logic.SettingsManager(this)

        try {
            val bitmap = if (fileName == "prediction_board.png") {
                com.magic.calculator.utils.PredictionOverlay.getOverlayBitmap(this, settingsManager.forcedGalleryTime, 1)
            } else if (fileName == "prediction_friends.jpg") {
                com.magic.calculator.utils.PredictionFriendsOverlay.getFriendsOverlayBitmap(
                    this, settingsManager.forcedContactName, settingsManager.forcedContactPhone, 1
                ) ?: BitmapFactory.decodeStream(assets.open("gallery/$fileName"))
            } else {
                BitmapFactory.decodeStream(assets.open("gallery/$fileName"))
            }

            if (bitmap == null) {
                Toast.makeText(this, "Failed to load image for sharing", Toast.LENGTH_SHORT).show()
                return
            }

            val cacheFile = java.io.File(cacheDir, fileName)
            val outputStream = java.io.FileOutputStream(cacheFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
            outputStream.close()

            val uri = androidx.core.content.FileProvider.getUriForFile(
                this, 
                "$packageName.fileprovider", 
                cacheFile
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share Photo"))
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback text share if it fails
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "Check out this photo: $fileName")
            }
            startActivity(Intent.createChooser(intent, "Share Photo"))
        }
    }

    private fun showFilterDialog() {
        val currentPos = viewPager.currentItem
        if (currentPos !in imageList.indices) return
        val fileName = imageList[currentPos]

        val options = arrayOf("Original", "Grayscale (B&W)", "Vintage (Sepia)", "Negative (Invert)")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Apply Filter")
            .setItems(options) { _, which ->
                val filter = when (which) {
                    1 -> "grayscale"
                    2 -> "sepia"
                    3 -> "invert"
                    else -> "original"
                }
                imageFilters[fileName] = filter
                adapter.notifyItemChanged(currentPos)
            }
            .show()
    }

    private fun confirmDelete() {
        val currentPos = viewPager.currentItem
        if (currentPos !in imageList.indices) return

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Delete Photo")
            .setMessage("Are you sure you want to delete this photo?")
            .setPositiveButton("Delete") { _, _ ->
                imageList.removeAt(currentPos)
                if (imageList.isEmpty()) {
                    finish()
                } else {
                    adapter.notifyItemRemoved(currentPos)
                    val nextPos = minOf(currentPos, imageList.size - 1)
                    viewPager.setCurrentItem(nextPos, false)
                    tvCount.text = "${nextPos + 1}/${imageList.size}"
                    updateHeartState()
                }
                Toast.makeText(this, "Photo deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showMoreMenu() {
        val btnMore = findViewById<View>(R.id.btnMore)
        val popup = androidx.appcompat.widget.PopupMenu(this, btnMore)
        popup.menu.add(0, 1, 0, "Details")
        popup.menu.add(0, 2, 1, "Rotate Left (90°)")
        popup.menu.add(0, 3, 2, "Rotate Right (90°)")
        popup.menu.add(0, 4, 3, "Set as Wallpaper")
        
        popup.setOnMenuItemClickListener { item ->
            val currentPos = viewPager.currentItem
            if (currentPos in imageList.indices) {
                val fileName = imageList[currentPos]
                when (item.itemId) {
                    1 -> showMetadataDialog(fileName, currentPos + 1)
                    2 -> {
                        val currentRotation = imageRotations[fileName] ?: 0f
                        imageRotations[fileName] = currentRotation - 90f
                        adapter.notifyItemChanged(currentPos)
                    }
                    3 -> {
                        val currentRotation = imageRotations[fileName] ?: 0f
                        imageRotations[fileName] = currentRotation + 90f
                        adapter.notifyItemChanged(currentPos)
                    }
                    4 -> {
                        setAsWallpaper(fileName)
                    }
                }
            }
            true
        }
        popup.show()
    }

    private fun setAsWallpaper(fileName: String) {
        try {
            val settingsManager = com.magic.calculator.logic.SettingsManager(this)
            val bitmap = if (fileName == "prediction_board.png") {
                com.magic.calculator.utils.PredictionOverlay.getOverlayBitmap(this, settingsManager.forcedGalleryTime, 1)
            } else if (fileName == "prediction_friends.jpg") {
                com.magic.calculator.utils.PredictionFriendsOverlay.getFriendsOverlayBitmap(
                    this, settingsManager.forcedContactName, settingsManager.forcedContactPhone, 1
                ) ?: BitmapFactory.decodeStream(assets.open("gallery/$fileName"))
            } else {
                BitmapFactory.decodeStream(assets.open("gallery/$fileName"))
            }
            if (bitmap == null) {
                Toast.makeText(this, "Failed to load image for wallpaper", Toast.LENGTH_SHORT).show()
                return
            }
            val wallpaperManager = android.app.WallpaperManager.getInstance(this)
            wallpaperManager.setBitmap(bitmap)
            Toast.makeText(this, "Wallpaper set successfully!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to set wallpaper", Toast.LENGTH_SHORT).show()
        }
    }

    private var isBarsVisible = true

    fun toggleBars() {
        val topBar = findViewById<android.view.View>(R.id.topBar)
        val bottomBar = findViewById<android.view.View>(R.id.bottomBar)
        val tvRaw = findViewById<android.view.View>(R.id.tvRaw)
        
        isBarsVisible = !isBarsVisible
        
        val visibility = if (isBarsVisible) android.view.View.VISIBLE else android.view.View.GONE
        topBar.visibility = visibility
        bottomBar.visibility = visibility
        tvRaw.visibility = visibility

        // System bars toggle
        val controller = androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
        if (isBarsVisible) {
            controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        } else {
            controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun showMetadataDialog(fileName: String, position: Int) {
        val container = findViewById<View>(R.id.metadataContainer)
        val tvMetaName = findViewById<TextView>(R.id.tvMetaName)
        val tvMetaTime = findViewById<TextView>(R.id.tvMetaTime)
        val tvMetaSize = findViewById<TextView>(R.id.tvMetaSize)
        val tvMetaParameters = findViewById<TextView>(R.id.tvMetaParameters)
        val tvMetaPath = findViewById<TextView>(R.id.tvMetaPath)
        val ivMetaHistogram = findViewById<ImageView>(R.id.ivMetaHistogram)

        val settingsManager = com.magic.calculator.logic.SettingsManager(this)

        if (fileName == "prediction_board.png") {
            val cal = Calendar.getInstance()
            val timeClean = settingsManager.forcedGalleryTime.replace(".", ":").trim()
            var h = 6
            var m = 19
            try {
                val parts = timeClean.split(":")
                if (parts.size >= 2) {
                    h = parts[0].toInt()
                    m = parts[1].toInt()
                } else if (timeClean.length == 4) {
                    h = timeClean.substring(0, 2).toInt()
                    m = timeClean.substring(2, 4).toInt()
                } else if (timeClean.length == 3) {
                    h = timeClean.substring(0, 1).toInt()
                    m = timeClean.substring(1, 3).toInt()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val customFileName = "PIN_${h}_${String.format("%02d", m)}.jpg"
            tvMetaName.text = customFileName
            tvMetaSize.text = "179 KB  736 × 1288"
            tvMetaParameters.text = "No camera information"
            tvMetaPath.text = "Internal storage/Pictures/100PINT/Pins/$customFileName"

            cal.set(Calendar.HOUR_OF_DAY, h)
            cal.set(Calendar.MINUTE, m)
            
            val sdf = SimpleDateFormat("M/d/yy h:mm a", Locale.US)
            tvMetaTime.text = sdf.format(cal.time)
        } else if (fileName == "prediction_friends.jpg") {
            val customFileName = "IMG_CON_FORCE.jpg"
            tvMetaName.text = customFileName
            tvMetaSize.text = "284 KB  1080 × 1920"
            tvMetaParameters.text = "No camera information"
            tvMetaPath.text = "Internal storage/Pictures/100PINT/Gallery/$customFileName"

            val cal = Calendar.getInstance()
            cal.add(Calendar.MINUTE, -12)
            val sdf = SimpleDateFormat("M/d/yy h:mm a", Locale.US)
            tvMetaTime.text = sdf.format(cal.time)
        } else {
            val trimmedName = if (fileName.length > 32) fileName.substring(0, 28) + "..." else fileName
            tvMetaName.text = trimmedName
            tvMetaParameters.text = "No camera information"
            tvMetaPath.text = "Internal storage/Pictures/100PINT/Gallery/$fileName"

            val sizeKb = try {
                val fd = assets.openFd("gallery/$fileName")
                val len = fd.length
                fd.close()
                len / 1024
            } catch (e: Exception) {
                120 + position * 15
            }
            tvMetaSize.text = "$sizeKb KB  736 × 1288"

            val cal = Calendar.getInstance()
            cal.add(Calendar.MINUTE, -(position * 17))
            val sdf = SimpleDateFormat("M/d/yy h:mm a", Locale.US)
            tvMetaTime.text = sdf.format(cal.time)
        }

        drawMockHistogram(ivMetaHistogram)
        container.visibility = View.VISIBLE
    }

    private fun drawMockHistogram(imageView: ImageView) {
        val width = 500
        val height = 120
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.parseColor("#1C1917"))

        val paint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
        }

        val pathRed = Path()
        val pathGreen = Path()
        val pathBlue = Path()
        val pathCyan = Path()
        
        pathRed.moveTo(0f, height.toFloat())
        pathGreen.moveTo(0f, height.toFloat())
        pathBlue.moveTo(0f, height.toFloat())
        pathCyan.moveTo(0f, height.toFloat())

        for (i in 0..width) {
            val x = i.toFloat()
            val yRed = height - (35 + 35 * Math.sin(x * 0.012).toFloat() + 15 * Math.sin(x * 0.035).toFloat() + 5 * Math.sin(x * 0.06).toFloat())
            val yGreen = height - (45 + 40 * Math.sin((x - 80) * 0.015).toFloat() + 12 * Math.sin(x * 0.04).toFloat())
            val yBlue = height - (30 + 45 * Math.sin((x + 120) * 0.018).toFloat() + 8 * Math.sin(x * 0.05).toFloat())
            val yCyan = height - (15 + 25 * Math.sin((x - 180) * 0.022).toFloat())

            pathRed.lineTo(x, Math.max(8f, Math.min(height.toFloat() - 2f, yRed)))
            pathGreen.lineTo(x, Math.max(8f, Math.min(height.toFloat() - 2f, yGreen)))
            pathBlue.lineTo(x, Math.max(8f, Math.min(height.toFloat() - 2f, yBlue)))
            pathCyan.lineTo(x, Math.max(8f, Math.min(height.toFloat() - 2f, yCyan)))
        }

        pathRed.lineTo(width.toFloat(), height.toFloat())
        pathGreen.lineTo(width.toFloat(), height.toFloat())
        pathBlue.lineTo(width.toFloat(), height.toFloat())
        pathCyan.lineTo(width.toFloat(), height.toFloat())

        paint.color = Color.parseColor("#77EF4444")
        canvas.drawPath(pathRed, paint)

        paint.color = Color.parseColor("#7722C55E")
        canvas.drawPath(pathGreen, paint)

        paint.color = Color.parseColor("#773B82F6")
        canvas.drawPath(pathBlue, paint)

        paint.color = Color.parseColor("#7706B6D4")
        canvas.drawPath(pathCyan, paint)

        imageView.setImageBitmap(bitmap)
    }
}
