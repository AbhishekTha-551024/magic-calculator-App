package com.magic.calculator.core.overlay

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
            // Name & Phone Formatting Logic (Clear, Bold & Complete)
            // ----------------------------------------------------
            val displayName = if (nameText.isNotBlank()) nameText.trim().uppercase() else "ABHA GUPTA"
            val displayPhone = if (numberText.isNotBlank()) numberText.trim() else "+91 98765 43210"

            // ----------------------------------------------------
            // Paint Setup
            // ----------------------------------------------------
            val badgeBgPaint = Paint().apply {
                color = Color.WHITE
                isAntiAlias = true
                style = Paint.Style.FILL
            }

            val textPrimaryPaint = Paint().apply {
                color = Color.parseColor("#111827") // Ultra-crisp dark charcoal black
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
                textSize = w * 0.027f
            }

            val textSecondaryPaint = Paint().apply {
                color = Color.parseColor("#4B5563") // Professional Slate Gray
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
                textSize = w * 0.020f
            }

            // ----------------------------------------------------
            // 1. LEFT BADGE (Purple Blazer Friend)
            // Displays: FULL NAME prominently + Phone Number below
            // ----------------------------------------------------
            val leftCx = w * 0.396f
            val leftCy = h * 0.648f
            val leftBw = w * 0.170f
            val leftBh = h * 0.065f

            canvas.save()
            canvas.rotate(-0.5f, leftCx, leftCy)
            
            // Clean white badge sticker
            val leftRect = RectF(leftCx - leftBw * 0.5f, leftCy - leftBh * 0.5f, leftCx + leftBw * 0.5f, leftCy + leftBh * 0.5f)
            canvas.drawRoundRect(leftRect, w * 0.005f, w * 0.005f, badgeBgPaint)

            // Draw full Name as primary headline, Phone as secondary
            canvas.drawText(displayName, leftCx, leftCy - leftBh * 0.06f, textPrimaryPaint)
            canvas.drawText(displayPhone, leftCx, leftCy + leftBh * 0.34f, textSecondaryPaint)
            
            canvas.restore()

            // ----------------------------------------------------
            // 2. RIGHT BADGE (Maroon Blazer Friend)
            // Displays: FULL PHONE NUMBER prominently + Name below
            // ----------------------------------------------------
            val rightCx = w * 0.654f
            val rightCy = h * 0.672f
            val rightBw = w * 0.155f
            val rightBh = h * 0.060f

            canvas.save()
            canvas.rotate(0.5f, rightCx, rightCy)
            
            // Clean white badge sticker
            val rightRect = RectF(rightCx - rightBw * 0.5f, rightCy - rightBh * 0.5f, rightCx + rightBw * 0.5f, rightCy + rightBh * 0.5f)
            canvas.drawRoundRect(rightRect, w * 0.005f, w * 0.005f, badgeBgPaint)

            // Draw full Phone Number as primary headline, Name as secondary
            canvas.drawText(displayPhone, rightCx, rightCy - rightBh * 0.06f, textPrimaryPaint)
            canvas.drawText(displayName, rightCx, rightCy + rightBh * 0.34f, textSecondaryPaint)

            canvas.restore()

            mutableBitmap
        } catch (e: Exception) {
            Log.e("PredictionFriendsOverlay", "Error overlaying friends photo", e)
            null
        }
    }
}
