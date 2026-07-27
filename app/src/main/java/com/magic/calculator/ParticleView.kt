package com.magic.calculator

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.random.Random

/**
 * Custom View for high-performance magic particles.
 */
class ParticleView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val particles = mutableListOf<Particle>()
    private val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    data class Particle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var radius: Float,
        var alpha: Int,
        val color: Int
    )

    fun burst(x: Float, y: Float, color: Int) {
        repeat(50) {
            particles.add(
                Particle(
                    x, y,
                    (Random.nextFloat() - 0.5f) * 20f,
                    (Random.nextFloat() - 0.5f) * 20f,
                    Random.nextFloat() * 8f + 2f,
                    255,
                    color
                )
            )
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            paint.color = p.color
            paint.alpha = p.alpha
            canvas.drawCircle(p.x, p.y, p.radius, paint)

            // Update particle
            p.x += p.vx
            p.y += p.vy
            p.alpha -= 5
            p.radius *= 0.95f

            if (p.alpha <= 0 || p.radius < 0.5f) {
                iterator.remove()
            }
        }

        if (particles.isNotEmpty()) {
            invalidate()
        }
    }
}
