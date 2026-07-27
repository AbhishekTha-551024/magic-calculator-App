package com.magic.calculator.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Log

object PredictionOverlay {

    /**
     * Loads the template clock dial image, draws static hour and minute hands showing 10:10 (wrong time),
     * and writes the dynamic snatched spectator time ONLY in microscopic text on the minute needle spine.
     * The hour needle is completely clean charcoal steel, and all large dial watermarks have been removed.
     */
    fun getOverlayBitmap(context: Context, timeText: String, sampleSize: Int = 1): Bitmap? {
        return try {
            val assetManager = context.assets
            val inputStream = assetManager.open("gallery/prediction_board.png")
            
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inMutable = true
            }
            
            val originalBitmap = BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()
            
            if (originalBitmap == null) return null
            
            val mutableBitmap = if (originalBitmap.isMutable) {
                originalBitmap
            } else {
                originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
            }
            
            val canvas = Canvas(mutableBitmap)
            val width = mutableBitmap.width.toFloat()
            val height = mutableBitmap.height.toFloat()
            
            // Center of the generated close-up clock face
            val centerX = width * 0.50f
            val centerY = height * 0.50f
            val radius = Math.min(width, height) * 0.35f
            
            // Parse timeText (e.g. "8.25" or "08:25")
            val timeClean = timeText.replace(".", ":").trim()
            var hour = 10
            var minute = 10
            try {
                val parts = timeClean.split(":")
                if (parts.size >= 2) {
                    hour = parts[0].toInt()
                    minute = parts[1].toInt()
                } else if (timeClean.length == 4) {
                    hour = timeClean.substring(0, 2).toInt()
                    minute = timeClean.substring(2, 4).toInt()
                } else if (timeClean.length == 3) {
                    hour = timeClean.substring(0, 1).toInt()
                    minute = timeClean.substring(1, 3).toInt()
                }
            } catch (e: Exception) {
                Log.e("PredictionOverlay", "Error parsing time, falling back to 10:10", e)
            }
            
            val formattedTime = String.format("%02d:%02d", hour, minute)
            
            // Static "Wrong" Time: 10:10 (standard aesthetic showcasing look)
            val hourAngle = 10 * 30f + 10 * 0.5f // 305 degrees
            val minuteAngle = 10 * 6f           // 60 degrees
            
            // Setup base hand paints (charcoal black steel look)
            val handPaint = Paint().apply {
                color = Color.parseColor("#18181B") // Zinc-900 slate black metal
                isAntiAlias = true
                strokeCap = Paint.Cap.ROUND
                style = Paint.Style.STROKE
            }
            
            // 1. HOUR HAND (Shorter, Thicker) - Points to 10 (Completely clean, no text)
            canvas.save()
            canvas.rotate(hourAngle, centerX, centerY)
            handPaint.strokeWidth = radius * 0.05f
            val hourLength = radius * 0.52f
            canvas.drawLine(centerX, centerY, centerX, centerY - hourLength, handPaint)
            canvas.restore()
            
            // 2. MINUTE HAND (Longer, Thinner) - Points to 2 (10 minutes)
            canvas.save()
            canvas.rotate(minuteAngle, centerX, centerY)
            handPaint.strokeWidth = radius * 0.03f
            val minuteLength = radius * 0.78f
            canvas.drawLine(centerX, centerY, centerX, centerY - minuteLength, handPaint)
            
            // Microscopic digital prediction engraved ONLY on the minute hand spine
            val minTextPaint = Paint().apply {
                color = Color.WHITE // Crisp high-contrast white
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
                textSize = radius * 0.024f
            }
            canvas.save()
            // Shift coordinates along the minute hand, rotate text along the needle
            canvas.translate(centerX, centerY - minuteLength * 0.55f)
            canvas.rotate(90f)
            canvas.drawText(formattedTime, 0f, radius * 0.008f, minTextPaint)
            canvas.restore()
            canvas.restore()
            
            // 3. SECOND SWEEP NEEDLE (Vintage sweep second hand at static 42s)
            canvas.save()
            canvas.rotate(42 * 6f, centerX, centerY)
            val secPaint = Paint().apply {
                color = Color.parseColor("#991B1B") // Crimson red
                isAntiAlias = true
                strokeWidth = radius * 0.01f
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawLine(centerX, centerY + radius * 0.15f, centerX, centerY - radius * 0.85f, secPaint)
            canvas.restore()
            
            // 4. PRESTIGE PIVOT HUB (Gold cap on the center pin)
            val pivotPaint = Paint().apply {
                color = Color.parseColor("#B45309") // Bronze gold center
                isAntiAlias = true
                style = Paint.Style.FILL
            }
            canvas.drawCircle(centerX, centerY, radius * 0.07f, pivotPaint)
            
            val pivotBorder = Paint().apply {
                color = Color.parseColor("#78350F") // Dark bronze gold border
                isAntiAlias = true
                style = Paint.Style.STROKE
                strokeWidth = radius * 0.015f
            }
            canvas.drawCircle(centerX, centerY, radius * 0.07f, pivotBorder)
            
            mutableBitmap
        } catch (e: Exception) {
            Log.e("PredictionOverlay", "Error creating overlay bitmap", e)
            null
        }
    }
}
