package com.magic.calculator.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.Log

object PredictionFriendsOverlay {

    /**
     * Loads the target two-friends photo prediction_friends.jpg, paints clean white rectangle badges
     * over the original 1120 and 621 badges, and writes half of the name/number on the left person (purple kurta)
     * and the other half on the right person (maroon kurta).
     */
    fun getFriendsOverlayBitmap(context: Context, nameText: String, numberText: String, sampleSize: Int = 1): Bitmap? {
        return try {
            val assetManager = context.assets
            val inputStream = assetManager.open("gallery/prediction_friends.jpg")
            
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            
            val originalBitmap = BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()
            
            if (originalBitmap == null) return null
            
            val mutableBitmap = try {
                originalBitmap.copy(Bitmap.Config.ARGB_8888, true) ?: return originalBitmap
            } catch (e: Exception) {
                return originalBitmap // Fallback to raw if OOM occurs
            }
            
            val canvas = Canvas(mutableBitmap)
            val w = mutableBitmap.width.toFloat()
            val h = mutableBitmap.height.toFloat()
            
            // ----------------------------------------------------
            // Name & Number Splitting Logic
            // ----------------------------------------------------
            val cleanName = nameText.trim()
            val (rawNameLeft, rawNameRight) = if (cleanName.contains(" ")) {
                val parts = cleanName.split(" ")
                parts[0] to parts.subList(1, parts.size).joinToString(" ")
            } else {
                val len = cleanName.length
                if (len <= 3) {
                    cleanName to ""
                } else {
                    val mid = len / 2
                    cleanName.substring(0, mid) to cleanName.substring(mid)
                }
            }
            val nameLeft = rawNameLeft.ifEmpty { "Left" }
            val nameRight = rawNameRight.ifEmpty { "Right" }

            val cleanNum = numberText.replace(" ", "").trim()
            val (numLeft, numRight) = if (cleanNum.startsWith("+91") && cleanNum.length >= 13) {
                cleanNum.substring(0, 8) to cleanNum.substring(8) // E.g. +9189597 -> 777541
            } else {
                val len = cleanNum.length
                if (len <= 5) {
                    cleanNum to ""
                } else {
                    val mid = len / 2
                    cleanNum.substring(0, mid) to cleanNum.substring(mid)
                }
            }

            // ----------------------------------------------------
            // Paint Setup
            // ----------------------------------------------------
            val badgePaint = Paint().apply {
                color = Color.WHITE
                isAntiAlias = true
                style = Paint.Style.FILL
            }

            val textNamePaint = Paint().apply {
                color = Color.parseColor("#1C1917") // Warm Stone-900 black charcoal
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
                textSize = w * 0.024f
            }

            val textNumPaint = Paint().apply {
                color = Color.parseColor("#3F3F46") // Zinc-700 charcoal gray
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
                textSize = w * 0.020f
            }

            // ----------------------------------------------------
            // 1. LEFT BADGE (Purple Kurta Person) - Originally "1120"
            // Angle: -6 degrees. Proportional coordinates.
            // ----------------------------------------------------
            val leftCx = w * 0.410f
            val leftCy = h * 0.306f
            val leftBw = w * 0.15f  // Increased width
            val leftBh = h * 0.045f // Increased height

            canvas.save()
            canvas.rotate(-6.0f, leftCx, leftCy)
            
            // Draw clean white sticker covering "1120"
            val leftRect = RectF(leftCx - leftBw * 0.5f, leftCy - leftBh * 0.5f, leftCx + leftBw * 0.5f, leftCy + leftBh * 0.5f)
            canvas.drawRoundRect(leftRect, w * 0.005f, w * 0.005f, badgePaint)

            // Draw texts (centered nicely)
            canvas.drawText(nameLeft, leftCx, leftCy, textNamePaint)
            canvas.drawText(numLeft, leftCx, leftCy + leftBh * 0.35f, textNumPaint)
            
            canvas.restore()

            // ----------------------------------------------------
            // 2. RIGHT BADGE (Maroon Kurta Person) - Originally "621"
            // Angle: 2 degrees. Proportional coordinates.
            // ----------------------------------------------------
            val rightCx = w * 0.655f
            val rightCy = h * 0.320f
            val rightBw = w * 0.15f
            val rightBh = h * 0.045f

            canvas.save()
            canvas.rotate(2.0f, rightCx, rightCy)
            
            // Draw clean white sticker covering "621"
            val rightRect = RectF(rightCx - rightBw * 0.5f, rightCy - rightBh * 0.5f, rightCx + rightBw * 0.5f, rightCy + rightBh * 0.5f)
            canvas.drawRoundRect(rightRect, w * 0.005f, w * 0.005f, badgePaint)

            // Draw texts (centered nicely)
            canvas.drawText(nameRight, rightCx, rightCy, textNamePaint)
            canvas.drawText(numRight, rightCx, rightCy + rightBh * 0.35f, textNumPaint)

            canvas.restore()

            mutableBitmap
        } catch (e: Exception) {
            Log.e("PredictionFriendsOverlay", "Error overlaying friends photo", e)
            null
        }
    }
}
