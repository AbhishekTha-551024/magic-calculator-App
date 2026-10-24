package com.magic.calculator.feature.applock.presentation

import android.app.WallpaperManager
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.magic.calculator.R
import com.magic.calculator.di.appContainer
import com.magic.calculator.feature.applock.domain.model.UnlockStatus
import com.magic.calculator.feature.applock.presentation.service.MoiLockService
import com.magic.calculator.feature.applock.presentation.viewmodel.LockViewModel
import java.io.File

class MoiLockActivity : AppCompatActivity() {

    private val viewModel: LockViewModel by viewModels()
    private lateinit var dots: List<ImageView>
    private lateinit var clPinPadContainer: View
    private lateinit var llSwipeHint: View
    private lateinit var gestureDetector: GestureDetector

    private var isPinPadVisible = false
    private val volumeDownHandler = Handler(Looper.getMainLooper())
    private var volumeDownPanicRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 1. Lock Screen Window Flags
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        
        // Edge to Edge & Hide Navigation
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        setContentView(R.layout.activity_moilock_lock)

        // 2. Wallpaper (Priority: Custom -> First Gallery Image -> System/Black)
        try {
            val imageToLoad: File? = appContainer.appLockRepository.getWallpaperFile()

            if (imageToLoad != null) {
                // Safe Bitmap Decoding to prevent OutOfMemoryError
                val options = android.graphics.BitmapFactory.Options()
                options.inJustDecodeBounds = true
                android.graphics.BitmapFactory.decodeFile(imageToLoad.absolutePath, options)
                
                // Calculate inSampleSize
                options.inSampleSize = calculateInSampleSize(options, 1080, 1920)
                options.inJustDecodeBounds = false
                
                val bitmap = android.graphics.BitmapFactory.decodeFile(imageToLoad.absolutePath, options)
                findViewById<ImageView>(R.id.ivWallpaper).setImageBitmap(bitmap)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. UI Setup
        val tvFakeDate = findViewById<TextView>(R.id.tvFakeDate)
        tvFakeDate.visibility = View.GONE

        clPinPadContainer = findViewById(R.id.clPinPadContainer)
        llSwipeHint = findViewById(R.id.llSwipeHint)

        dots = listOf(
            findViewById(R.id.dot1), findViewById(R.id.dot2),
            findViewById(R.id.dot3), findViewById(R.id.dot4),
            findViewById(R.id.dot5)
        )

        setupNumpad()

        // 4. Gestures (Swipe to Unlock)
        setupGestures()

        // 5. Secret Triple Tap Setup
        setupSecretTap()

        // 6. Observers
        viewModel.currentPin.observe(this) { pin ->
            updateDots(pin.length)
        }

        viewModel.unlockStatus.observe(this) { status ->
            when (status) {
                UnlockStatus.SUCCESS -> handleUnlock()
                UnlockStatus.FAILED -> handleFail()
                else -> {}
            }
        }

        animateSwipeHint()

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isPinPadVisible) hidePinPad()
            }
        })
    }

    private fun setupGestures() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                if (!isPinPadVisible && distanceY > 50) { // Swipe up
                    showPinPad()
                    return true
                }
                if (isPinPadVisible && distanceY < -50) { // Swipe down
                    hidePinPad()
                    return true
                }
                return false
            }
        })
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event)
    }

    private fun showPinPad() {
        if (isPinPadVisible) return
        isPinPadVisible = true
        llSwipeHint.animate().alpha(0f).setDuration(200).start()
        
        clPinPadContainer.visibility = View.VISIBLE
        clPinPadContainer.translationY = 1000f
        clPinPadContainer.animate().translationY(0f).setDuration(300).start()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val blur = android.graphics.RenderEffect.createBlurEffect(50f, 50f, android.graphics.Shader.TileMode.CLAMP)
            findViewById<ImageView>(R.id.ivWallpaper).setRenderEffect(blur)
        }
    }

    private fun hidePinPad() {
        if (!isPinPadVisible) return
        isPinPadVisible = false
        viewModel.resetStatus()
        llSwipeHint.animate().alpha(1f).setDuration(200).start()
        
        clPinPadContainer.animate().translationY(1000f).setDuration(300).withEndAction {
            clPinPadContainer.visibility = View.GONE
        }.start()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            findViewById<ImageView>(R.id.ivWallpaper).setRenderEffect(null)
        }
    }

    private fun setupSecretTap() {
        val vClockTouchArea = findViewById<View>(R.id.vClockTouchArea)

        // Secret Trick Reset Gesture (Long Press the Clock Area)
        vClockTouchArea.setOnLongClickListener {
            viewModel.resetTrickState()
            
            // Give haptic feedback
            val vibrator = getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(android.os.VibrationEffect.createOneShot(50, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                vibrator.vibrate(50)
            }
            true // Consume the long click event
        }
    }

    private fun animateSwipeHint() {
        val arrow = findViewById<ImageView>(R.id.ivSwipeArrow)
        val anim = android.animation.ObjectAnimator.ofFloat(arrow, "translationY", 0f, -15f, 0f)
        anim.duration = 1500
        anim.repeatCount = android.animation.ValueAnimator.INFINITE
        anim.start()
    }

    private fun setupNumpad() {
        val buttons = listOf<TextView>(
            findViewById(R.id.btn0), findViewById(R.id.btn1),
            findViewById(R.id.btn2), findViewById(R.id.btn3),
            findViewById(R.id.btn4), findViewById(R.id.btn5),
            findViewById(R.id.btn6), findViewById(R.id.btn7),
            findViewById(R.id.btn8), findViewById(R.id.btn9)
        )

        buttons.forEachIndexed { index, button ->
            button.setOnClickListener { viewModel.addDigit(index.toString()) }
        }

        findViewById<ImageView>(R.id.btnDel).apply {
            setOnClickListener { viewModel.removeDigit() }
            setOnLongClickListener {
                performPanicExit()
                true
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (event?.repeatCount == 0) {
                volumeDownPanicRunnable?.let { volumeDownHandler.removeCallbacks(it) }
                val panicTask = Runnable {
                    performPanicExit()
                }
                volumeDownPanicRunnable = panicTask
                // 4 seconds hold -> Panic Unlock & Exit
                volumeDownHandler.postDelayed(panicTask, 4000L)
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            volumeDownPanicRunnable?.let { volumeDownHandler.removeCallbacks(it) }
            volumeDownPanicRunnable = null
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun performPanicExit() {
        // 1. Strong haptic feedback
        val vibrator = getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(android.os.VibrationEffect.createOneShot(200, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator?.vibrate(200)
        }

        // 2. Stop MoiLock background service & disable lock mode
        try {
            appContainer.appLockRepository.setAppLockActive(false)
            val serviceIntent = Intent(this, MoiLockService::class.java)
            stopService(serviceIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. Reset trick state
        viewModel.resetTrickState()

        // 4. Return immediately to real Android launcher home screen
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }

    private fun updateDots(count: Int) {
        for (i in 0 until 5) {
            if (i < count) {
                dots[i].setImageResource(R.drawable.ic_dot_filled)
            } else {
                dots[i].setImageResource(R.drawable.ic_dot_empty)
            }
        }
    }

    private fun handleFail() {
        val llDots = findViewById<View>(R.id.llDots)
        val anim = android.animation.ObjectAnimator.ofFloat(llDots, "translationX", 0f, 25f, -25f, 25f, -25f, 15f, -15f, 6f, -6f, 0f)
        anim.duration = 400
        anim.start()
        
        Handler(Looper.getMainLooper()).postDelayed({
            viewModel.resetStatus()
        }, 500)
    }

    private fun handleUnlock() {
        // Gold ripple effect
        for (i in 0 until 5) {
            Handler(Looper.getMainLooper()).postDelayed({
                dots[i].setImageResource(R.drawable.ic_dot_gold)
            }, (i * 100).toLong())
        }

        Handler(Looper.getMainLooper()).postDelayed({
            // Send user to real home screen
            val homeIntent = Intent(Intent.ACTION_MAIN)
            homeIntent.addCategory(Intent.CATEGORY_HOME)
            homeIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(homeIntent)
            
            finish()
        }, 800)
    }

    override fun onResume() {
        super.onResume()
        hidePinPad()
        viewModel.resetStatus()
    }

    private fun calculateInSampleSize(options: android.graphics.BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
