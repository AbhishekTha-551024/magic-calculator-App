package com.magic.calculator.core.ui.physics

import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.BounceInterpolator
import android.view.animation.DecelerateInterpolator
import com.magic.calculator.core.audio.SoundManager
import com.magic.calculator.core.haptic.HapticManager
import java.util.Random

/**
 * PhysicsExplosionEngine
 *
 * Encapsulates:
 * - Button scatter animations
 * - Live accelerometer tilt gravity simulation loop
 * - Smooth button grid restoration animations
 */
class PhysicsExplosionEngine(
    private val soundManager: SoundManager,
    private val hapticManager: HapticManager
) {
    var isButtonsExploded = false
        private set

    var lastExplodeTime = 0L
        private set

    var gravityX = 0f
    var gravityY = 0f

    private val physicsHandler = Handler(Looper.getMainLooper())
    private var physicsRunnable: Runnable? = null
    private val velX = mutableMapOf<Int, Float>()
    private val velY = mutableMapOf<Int, Float>()

    fun explodeButtons(
        buttonGrid: ViewGroup,
        tvDisplay: View,
        tvFormula: View,
        mainLayout: View
    ) {
        if (isButtonsExploded) return
        isButtonsExploded = true
        lastExplodeTime = System.currentTimeMillis()

        soundManager.playMagicSwoosh()
        hapticManager.magicVibrate()

        val random = Random()
        tvFormula.animate().alpha(0f).setDuration(200L).start()
        velX.clear()
        velY.clear()

        val count = buttonGrid.childCount
        for (i in 0..count) {
            velX[i] = (random.nextFloat() - 0.5f) * 6f
            velY[i] = (random.nextFloat() - 0.5f) * 6f
        }

        for (i in 0 until count) {
            val view = buttonGrid.getChildAt(i)
            view.translationZ = 100f
            val randomX = (random.nextInt(200) - 100).toFloat()
            val randomRot = (random.nextInt(70) - 35).toFloat()
            val randomFall = 200f + random.nextInt(300).toFloat()

            view.animate().translationY(-200f).setDuration(250L)
                .setInterpolator(AccelerateDecelerateInterpolator()).withEndAction {
                    view.animate().translationY(randomFall).translationX(randomX)
                        .rotation(randomRot).alpha(1f)
                        .setInterpolator(BounceInterpolator()).setDuration(1000L)
                        .withEndAction {
                            if (i == count - 1 && isButtonsExploded) {
                                startPhysicsLoop(mainLayout, buttonGrid, tvDisplay)
                            }
                        }
                        .start()
                }.start()
        }

        tvDisplay.animate().alpha(0f).translationY(400f).setDuration(400L).start()
    }

    fun startPhysicsLoop(
        mainLayout: View,
        buttonGrid: ViewGroup,
        tvDisplay: View
    ) {
        stopPhysicsLoop()
        val screenW = mainLayout.width.toFloat()
        val screenH = mainLayout.height.toFloat()
        val count = buttonGrid.childCount
        val rng = Random()
        val mass = FloatArray(count + 1) { 0.5f + rng.nextFloat() * 1.0f }
        val damping = FloatArray(count + 1) { 0.84f + rng.nextFloat() * 0.12f }

        physicsRunnable = object : Runnable {
            override fun run() {
                try {
                    if (!isButtonsExploded) return
                    val gx = -gravityX
                    val gy = gravityY

                    for (i in 0 until count) {
                        val view = buttonGrid.getChildAt(i)
                        var vx = velX.getOrDefault(i, 0f)
                        var vy = velY.getOrDefault(i, 0f)
                        val accel = 0.5f / mass[i]
                        val damp = damping[i]

                        vx += gx * accel
                        vy += gy * accel
                        vx += (rng.nextFloat() - 0.5f) * 0.5f
                        vy += (rng.nextFloat() - 0.5f) * 0.5f
                        vx *= damp
                        vy *= damp

                        var tx = view.translationX + vx
                        var ty = view.translationY + vy
                        val maxX = screenW * 0.42f
                        val maxY = screenH * 0.50f
                        val minY = -screenH * 0.30f

                        if (tx > maxX) { tx = maxX; vx = -vx * 0.35f }
                        if (tx < -maxX) { tx = -maxX; vx = -vx * 0.35f }
                        if (ty > maxY) { ty = maxY; vy = -vy * 0.35f }
                        if (ty < minY) { ty = minY; vy = -vy * 0.35f }

                        view.translationX = tx
                        view.translationY = ty
                        velX[i] = vx
                        velY[i] = vy
                    }

                    val di = count
                    var vx = velX.getOrDefault(di, 0f)
                    var vy = velY.getOrDefault(di, 0f)
                    val accel = 0.5f / mass[di]

                    vx += gx * accel + (rng.nextFloat() - 0.5f) * 0.3f
                    vy += gy * accel + (rng.nextFloat() - 0.5f) * 0.3f
                    vx *= damping[di]
                    vy *= damping[di]

                    var tx = tvDisplay.translationX + vx
                    var ty = tvDisplay.translationY + vy

                    if (tx > screenW * 0.35f) { tx = screenW * 0.35f; vx = -vx * 0.35f }
                    if (tx < -screenW * 0.35f) { tx = -screenW * 0.35f; vx = -vx * 0.35f }
                    if (ty > screenH * 0.30f) { ty = screenH * 0.30f; vy = -vy * 0.35f }
                    if (ty < -screenH * 0.20f) { ty = -screenH * 0.20f; vy = -vy * 0.35f }

                    tvDisplay.translationX = tx
                    tvDisplay.translationY = ty
                    velX[di] = vx
                    velY[di] = vy

                    physicsHandler.postDelayed(this, 16L)
                } catch (e: Throwable) {
                    com.magic.calculator.core.logic.CrashReportingManager.logNonFatal(
                        e,
                        "PhysicsExplosionEngine tick loop error"
                    )
                }
            }
        }
        physicsHandler.post(physicsRunnable!!)
    }

    fun stopPhysicsLoop() {
        physicsRunnable?.let { physicsHandler.removeCallbacks(it) }
        physicsRunnable = null
    }

    fun restoreButtons(
        buttonGrid: ViewGroup,
        tvDisplay: View,
        tvFormula: View
    ) {
        if (!isButtonsExploded) return
        isButtonsExploded = false
        stopPhysicsLoop()

        soundManager.playMagicSwoosh()
        hapticManager.magicVibrate()

        for (i in 0 until buttonGrid.childCount) {
            val view = buttonGrid.getChildAt(i)
            view.translationZ = 0f
            view.animate().translationY(0f).translationX(0f).rotation(0f).alpha(1f)
                .setDuration(800L).setInterpolator(DecelerateInterpolator(1.5f)).start()
        }

        tvDisplay.animate().translationY(0f).translationX(0f).rotation(0f).alpha(1f)
            .setDuration(800L).setInterpolator(DecelerateInterpolator(1.5f)).start()

        tvFormula.animate().translationY(0f).alpha(1f).setDuration(800L).start()
    }
}
