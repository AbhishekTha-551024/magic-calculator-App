package com.magic.calculator.ui.moilock

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import java.util.Random

class ConfettiView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val particles = mutableListOf<Particle>()
    private val random = Random()
    private var isPlaying = true

    private val colors = intArrayOf(
        Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, Color.CYAN, Color.MAGENTA
    )

    init {
        for (i in 0 until 100) {
            particles.add(Particle())
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!isPlaying) return

        var allDead = true
        for (p in particles) {
            p.update(width, height)
            if (p.y < height) {
                allDead = false
                canvas.drawCircle(p.x, p.y, p.radius, p.paint)
            }
        }

        if (!allDead) {
            invalidate()
        }
    }

    inner class Particle {
        var x = 0f
        var y = 0f
        var speedX = 0f
        var speedY = 0f
        var radius = 0f
        val paint = Paint()

        init {
            paint.style = Paint.Style.FILL
            reset()
            // Start scattered
            y = random.nextFloat() * 1000f - 1000f
        }

        fun reset() {
            x = random.nextFloat() * 1000f // Will be updated when width is known
            y = -random.nextFloat() * 500f
            speedX = (random.nextFloat() - 0.5f) * 10f
            speedY = random.nextFloat() * 15f + 5f
            radius = random.nextFloat() * 10f + 5f
            paint.color = colors[random.nextInt(colors.size)]
        }

        fun update(w: Int, h: Int) {
            if (x == 0f) x = random.nextFloat() * w
            x += speedX
            y += speedY
            // Add some flutter
            x += (random.nextFloat() - 0.5f) * 5f
        }
    }
}
