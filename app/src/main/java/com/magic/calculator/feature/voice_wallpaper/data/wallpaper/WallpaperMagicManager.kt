package com.magic.calculator.feature.voice_wallpaper.data.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.util.Log
import com.magic.calculator.feature.voice_wallpaper.data.voice.VoiceCardExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object WallpaperMagicManager {

    private const val TAG = "WallpaperMagicManager"

    enum class Target(val flag: Int) {
        BOTH(WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK),
        HOME(WallpaperManager.FLAG_SYSTEM),
        LOCK(WallpaperManager.FLAG_LOCK)
    }

    /**
     * Renders a high-resolution wallpaper with the given number and applies it to system/lock screen.
     */
    suspend fun applyNumberWallpaper(
        context: Context,
        number: String,
        styleIndex: Int = 0,
        target: Target = Target.BOTH
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val displayMetrics = context.resources.displayMetrics
            // Use exact portrait screen dimensions so the text is perfectly centered and never cropped
            val width = if (displayMetrics.widthPixels > 0) displayMetrics.widthPixels else 1080
            val height = if (displayMetrics.heightPixels > 0) displayMetrics.heightPixels else 2400

            Log.d(TAG, "Rendering number wallpaper: '$number', size: ${width}x${height}, style: $styleIndex, target: ${target.name}")
            val bitmap = renderWallpaperBitmap(width, height, number, styleIndex)
            val wallpaperManager = WallpaperManager.getInstance(context)
            applyBitmapToWallpaper(wallpaperManager, bitmap, target)
        } catch (e: Exception) {
            Log.e(TAG, "Top-level failure in applyNumberWallpaper: ${e.message}", e)
            false
        }
    }

    /**
     * Renders a 24K Luxury Pure Gold Foil Playing Card wallpaper.
     */
    suspend fun applyCardWallpaper(
        context: Context,
        cardCode: String,
        styleIndex: Int = 0,
        target: Target = Target.BOTH
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val card = VoiceCardExtractor.getCardByCode(cardCode) 
                ?: VoiceCardExtractor.extractCard(cardCode) 
                ?: VoiceCardExtractor.getCardByCode("AS")!!

            val displayMetrics = context.resources.displayMetrics
            val width = if (displayMetrics.widthPixels > 0) displayMetrics.widthPixels else 1080
            val height = if (displayMetrics.heightPixels > 0) displayMetrics.heightPixels else 2400

            Log.d(TAG, "Rendering card wallpaper: '${card.englishName}', size: ${width}x${height}, style: $styleIndex, target: ${target.name}")
            val bitmap = render24kGoldCardWallpaperBitmap(width, height, card)
            val wallpaperManager = WallpaperManager.getInstance(context)
            applyBitmapToWallpaper(wallpaperManager, bitmap, target)
        } catch (e: Exception) {
            Log.e(TAG, "Top-level failure in applyCardWallpaper: ${e.message}", e)
            false
        }
    }

    private fun applyBitmapToWallpaper(
        wallpaperManager: WallpaperManager,
        bitmap: Bitmap,
        target: Target
    ): Boolean {
        var applySuccess = false
        Log.d(TAG, "Applying wallpaper to target: ${target.name} (flag=${target.flag})")

        fun setViaStream(flag: Int?): Boolean {
            return try {
                val stream = java.io.ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.PNG, 95, stream)
                val bytes = stream.toByteArray()
                if (flag != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    wallpaperManager.setStream(java.io.ByteArrayInputStream(bytes), null, true, flag)
                } else {
                    wallpaperManager.setStream(java.io.ByteArrayInputStream(bytes))
                }
                Log.d(TAG, "setStream succeeded for flag: $flag")
                true
            } catch (e: Exception) {
                Log.w(TAG, "setStream failed (flag=$flag): ${e.message}")
                false
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (target == Target.BOTH) {
                var homeDone = false
                var lockDone = false
                // 1. Try Home Screen (FLAG_SYSTEM)
                try {
                    wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM)
                    homeDone = true
                    Log.d(TAG, "setBitmap FLAG_SYSTEM succeeded")
                } catch (e: Exception) {
                    Log.w(TAG, "setBitmap FLAG_SYSTEM failed: ${e.message}, trying setStream...")
                    homeDone = setViaStream(WallpaperManager.FLAG_SYSTEM)
                }
                // 2. Try Lock Screen (FLAG_LOCK)
                try {
                    wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
                    lockDone = true
                    Log.d(TAG, "setBitmap FLAG_LOCK succeeded")
                } catch (e: Exception) {
                    Log.w(TAG, "setBitmap FLAG_LOCK failed (often restricted by ColorOS/Realme): ${e.message}, trying setStream...")
                    lockDone = setViaStream(WallpaperManager.FLAG_LOCK)
                }
                applySuccess = homeDone || lockDone
            } else {
                try {
                    wallpaperManager.setBitmap(bitmap, null, true, target.flag)
                    applySuccess = true
                    Log.d(TAG, "setBitmap ${target.name} succeeded")
                } catch (e: Exception) {
                    Log.w(TAG, "setBitmap ${target.name} failed: ${e.message}, trying setStream...")
                    applySuccess = setViaStream(target.flag)
                }
            }
        }

        // Global fallback if OEM blocked specific flags
        if (!applySuccess) {
            try {
                wallpaperManager.setBitmap(bitmap)
                applySuccess = true
                Log.d(TAG, "Standard setBitmap succeeded")
            } catch (e: Exception) {
                Log.w(TAG, "Standard setBitmap failed: ${e.message}, trying general stream...")
                applySuccess = setViaStream(null)
            }
        }

        Log.d(TAG, "Final wallpaper apply result: $applySuccess")
        return applySuccess
    }

    /**
     * Restores a clean, minimal elegant dark wallpaper.
     */
    suspend fun resetToDefaultWallpaper(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val displayMetrics = context.resources.displayMetrics
            val width = if (displayMetrics.widthPixels > 0) displayMetrics.widthPixels else 1080
            val height = if (displayMetrics.heightPixels > 0) displayMetrics.heightPixels else 2400

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val bgPaint = Paint().apply {
                shader = LinearGradient(
                    0f, 0f, 0f, height.toFloat(),
                    Color.parseColor("#090A0F"),
                    Color.parseColor("#121420"),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

            val wallpaperManager = WallpaperManager.getInstance(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
            } else {
                wallpaperManager.setBitmap(bitmap)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reset wallpaper", e)
            false
        }
    }

    // ── Number Bitmap Renderer ──────────────────────────────────────────────────

    private fun renderWallpaperBitmap(w: Int, h: Int, number: String, styleIndex: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val cx = w / 2f
        val cy = h * 0.46f

        when (styleIndex) {
            1 -> renderMinimalGoldStyle(canvas, w, h, cx, cy, number)
            2 -> renderMysticCosmicStyle(canvas, w, h, cx, cy, number)
            else -> renderCyberNeonStyle(canvas, w, h, cx, cy, number)
        }

        return bitmap
    }

    private fun renderCyberNeonStyle(canvas: Canvas, w: Int, h: Int, cx: Float, cy: Float, number: String) {
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, h.toFloat(),
                Color.parseColor("#060810"),
                Color.parseColor("#0F1426"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)

        val radialGlowPaint = Paint().apply {
            isAntiAlias = true
            shader = RadialGradient(
                cx, cy, w * 0.45f,
                intArrayOf(Color.parseColor("#3300FFFF"), Color.parseColor("#157B2CBF"), Color.TRANSPARENT),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(cx, cy, w * 0.45f, radialGlowPaint)

        val ringPaint = Paint().apply {
            color = Color.parseColor("#1A00FFFF")
            style = Paint.Style.STROKE
            strokeWidth = w * 0.003f
            isAntiAlias = true
        }
        canvas.drawCircle(cx, cy, w * 0.32f, ringPaint)

        val textPaint = Paint().apply {
            color = Color.parseColor("#FFFFFF")
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            textSize = w * 0.28f
            setShadowLayer(w * 0.05f, 0f, 0f, Color.parseColor("#00FFFF"))
        }

        val subPaint = Paint().apply {
            color = Color.parseColor("#80FFFFFF")
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            textSize = w * 0.032f
            letterSpacing = 0.25f
        }

        canvas.drawText(number, cx, cy + (w * 0.10f), textPaint)
        canvas.drawText("ENERGY FREQUENCY", cx, cy + (w * 0.24f), subPaint)
    }

    private fun renderMinimalGoldStyle(canvas: Canvas, w: Int, h: Int, cx: Float, cy: Float, number: String) {
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, h.toFloat(),
                Color.parseColor("#0A0A0A"),
                Color.parseColor("#141414"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)

        val haloPaint = Paint().apply {
            isAntiAlias = true
            shader = RadialGradient(
                cx, cy, w * 0.40f,
                intArrayOf(Color.parseColor("#25FFD700"), Color.parseColor("#0A854D0E"), Color.TRANSPARENT),
                floatArrayOf(0f, 0.6f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(cx, cy, w * 0.40f, haloPaint)

        val framePaint = Paint().apply {
            color = Color.parseColor("#33FFD700")
            style = Paint.Style.STROKE
            strokeWidth = w * 0.002f
            isAntiAlias = true
        }
        val frameRect = RectF(cx - w * 0.30f, cy - w * 0.25f, cx + w * 0.30f, cy + w * 0.25f)
        canvas.drawRoundRect(frameRect, w * 0.02f, w * 0.02f, framePaint)

        val textPaint = Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("serif", Typeface.BOLD)
            textSize = w * 0.26f
            shader = LinearGradient(
                cx, cy - w * 0.15f, cx, cy + w * 0.15f,
                Color.parseColor("#FFF3B0"),
                Color.parseColor("#D4AF37"),
                Shader.TileMode.CLAMP
            )
            setShadowLayer(w * 0.04f, 0f, 0f, Color.parseColor("#FFD700"))
        }

        val subPaint = Paint().apply {
            color = Color.parseColor("#99D4AF37")
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("serif", Typeface.NORMAL)
            textSize = w * 0.028f
            letterSpacing = 0.3f
        }

        canvas.drawText(number, cx, cy + (w * 0.09f), textPaint)
        canvas.drawText("DESTINY MATRIX", cx, cy + (w * 0.21f), subPaint)
    }

    private fun renderMysticCosmicStyle(canvas: Canvas, w: Int, h: Int, cx: Float, cy: Float, number: String) {
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, h.toFloat(),
                Color.parseColor("#050510"),
                Color.parseColor("#1A0B2E"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)

        val auraPaint = Paint().apply {
            isAntiAlias = true
            shader = RadialGradient(
                cx, cy, w * 0.45f,
                intArrayOf(Color.parseColor("#33F72585"), Color.parseColor("#157209B7"), Color.TRANSPARENT),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(cx, cy, w * 0.45f, auraPaint)

        val textPaint = Paint().apply {
            color = Color.parseColor("#FFFFFF")
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            textSize = w * 0.28f
            setShadowLayer(w * 0.06f, 0f, 0f, Color.parseColor("#F72585"))
        }

        val subPaint = Paint().apply {
            color = Color.parseColor("#CCF72585")
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            textSize = w * 0.030f
            letterSpacing = 0.25f
        }

        canvas.drawText(number, cx, cy + (w * 0.10f), textPaint)
        canvas.drawText("COSMIC ALIGNMENT", cx, cy + (w * 0.23f), subPaint)
    }

    // ── 24K Pure Gold Foil Luxury Playing Card Renderer ──────────────────────────

    /**
     * Renders a 100% realistic, ultra-luxurious 24K Pure Gold Foil Playing Card.
     * Features authentic gold leaf texture, 3D embossed border, genuine casino pips/court portraits,
     * and NO artificial bottom text!
     */
    private fun render24kGoldCardWallpaperBitmap(w: Int, h: Int, card: VoiceCardExtractor.PlayingCard): Bitmap {
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val cx = w / 2f
        val cy = h * 0.48f // Perfectly centered on phone screen

        // 1. Deep Midnight Velvet / Obsidian Casino Table Background
        val bgPaint = Paint().apply {
            shader = RadialGradient(
                cx, cy, h * 0.65f,
                intArrayOf(Color.parseColor("#161824"), Color.parseColor("#0A0B10"), Color.parseColor("#040507")),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)

        // 2. Warm 24K Gold Ambient Glow behind the card
        val haloPaint = Paint().apply {
            isAntiAlias = true
            shader = RadialGradient(
                cx, cy, w * 0.60f,
                intArrayOf(Color.parseColor("#45FFD700"), Color.parseColor("#15D4AF37"), Color.TRANSPARENT),
                floatArrayOf(0f, 0.50f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(cx, cy, w * 0.60f, haloPaint)

        // 3. Card Dimensions (Commanding luxury proportions)
        val cardW = w * 0.68f
        val cardH = cardW * 1.54f
        val cardLeft = cx - (cardW / 2f)
        val cardTop = cy - (cardH / 2f)
        val cardRight = cardLeft + cardW
        val cardBottom = cardTop + cardH
        val cardRadius = w * 0.038f

        val cardRect = RectF(cardLeft, cardTop, cardRight, cardBottom)

        // Deep 3D Cast Shadow
        val shadowPaint = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#99000000")
            setShadowLayer(w * 0.09f, 0f, w * 0.035f, Color.parseColor("#E6000000"))
        }
        canvas.drawRoundRect(cardRect, cardRadius, cardRadius, shadowPaint)

        // 4. 24K Pure Gold Leaf Card Body (Rich Metallic Sheen Gradient)
        val goldBodyPaint = Paint().apply {
            isAntiAlias = true
            shader = LinearGradient(
                cardLeft, cardTop, cardRight, cardBottom,
                intArrayOf(
                    Color.parseColor("#FFF3B0"), // Glistening light gold highlight
                    Color.parseColor("#FFDF00"), // Pure 24k gold
                    Color.parseColor("#E6B800"), // Warm medium gold
                    Color.parseColor("#D4AF37"), // Royal gold
                    Color.parseColor("#B8860B"), // Deep gold shadow
                    Color.parseColor("#FFEAA7"), // Reflective rim highlight
                    Color.parseColor("#E5A910")  // Rich bullion tone
                ),
                floatArrayOf(0f, 0.18f, 0.38f, 0.58f, 0.78f, 0.90f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(cardRect, cardRadius, cardRadius, goldBodyPaint)

        // 5. Fine Gold Micro-Texture / Sparkling Stipple Grid (Simulates Real Gold Leaf Grain)
        val texturePaint = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#15FFFFFF")
            strokeWidth = w * 0.0015f
        }
        val step = w * 0.012f
        var xCoord = cardLeft + (w * 0.02f)
        while (xCoord < cardRight - (w * 0.02f)) {
            canvas.drawLine(xCoord, cardTop + (w * 0.02f), xCoord, cardBottom - (w * 0.02f), texturePaint)
            xCoord += step
        }
        var yCoord = cardTop + (w * 0.02f)
        while (yCoord < cardBottom - (w * 0.02f)) {
            canvas.drawLine(cardLeft + (w * 0.02f), yCoord, cardRight - (w * 0.02f), yCoord, texturePaint)
            yCoord += step
        }

        // 6. Embossed 3D Golden Card Borders (Outer Bevel + Inner Filigree Frame)
        val outerBevelPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = w * 0.007f
            shader = LinearGradient(
                cardLeft, cardTop, cardRight, cardBottom,
                Color.parseColor("#FFFBEA"),
                Color.parseColor("#92400E"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(cardRect, cardRadius, cardRadius, outerBevelPaint)

        val margin1 = w * 0.022f
        val frame1Paint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = w * 0.0035f
            color = Color.parseColor("#80B45309")
        }
        canvas.drawRoundRect(
            RectF(cardLeft + margin1, cardTop + margin1, cardRight - margin1, cardBottom - margin1),
            cardRadius * 0.75f, cardRadius * 0.75f,
            frame1Paint
        )

        val margin2 = w * 0.034f
        val frame2Paint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = w * 0.0015f
            color = Color.parseColor("#CCFFD700")
        }
        canvas.drawRoundRect(
            RectF(cardLeft + margin2, cardTop + margin2, cardRight - margin2, cardBottom - margin2),
            cardRadius * 0.65f, cardRadius * 0.65f,
            frame2Paint
        )

        // 7. Corner Indices (Rank & Suit) - Authentic 24K Casino Styling
        val isRed = card.isRed
        val rankTextColor = if (isRed) Color.parseColor("#B91C1C") else Color.parseColor("#0F172A")
        val pipFillColor = if (isRed) Color.parseColor("#DC2626") else Color.parseColor("#1E293B")

        val cornerRankPaint = Paint().apply {
            isAntiAlias = true
            color = rankTextColor
            typeface = Typeface.create("serif", Typeface.BOLD)
            textSize = w * 0.095f
            textAlign = Paint.Align.CENTER
            setShadowLayer(w * 0.008f, w * 0.002f, w * 0.002f, Color.parseColor("#60000000"))
        }

        val cornerSuitPaint = Paint().apply {
            isAntiAlias = true
            color = pipFillColor
            typeface = Typeface.DEFAULT_BOLD
            textSize = w * 0.070f
            textAlign = Paint.Align.CENTER
            setShadowLayer(w * 0.008f, w * 0.002f, w * 0.002f, Color.parseColor("#50000000"))
        }

        // Top-Left Corner
        val tlX = cardLeft + (w * 0.082f)
        val tlRankY = cardTop + (w * 0.125f)
        val tlSuitY = tlRankY + (w * 0.072f)
        canvas.drawText(card.rank, tlX, tlRankY, cornerRankPaint)
        canvas.drawText(card.symbol, tlX, tlSuitY, cornerSuitPaint)

        // Bottom-Right Corner
        val brX = cardRight - (w * 0.082f)
        val brSuitY = cardBottom - (w * 0.055f)
        val brRankY = brSuitY - (w * 0.072f)
        canvas.drawText(card.rank, brX, brRankY, cornerRankPaint)
        canvas.drawText(card.symbol, brX, brSuitY, cornerSuitPaint)

        // 8. Center Content of the 24K Gold Card
        val innerW = cardW * 0.70f
        val innerH = cardH * 0.72f
        val innerLeft = cx - (innerW / 2f)
        val innerTop = cy - (innerH / 2f)
        val innerRight = innerLeft + innerW
        val innerBottom = innerTop + innerH

        when (card.rank) {
            "K", "Q", "J" -> {
                // Royal Casino Court Card Centerpiece (as in the 24k gold Queen card reference)
                renderRoyalCourtIllustration(canvas, w.toFloat(), cx, cy, innerLeft, innerTop, innerRight, innerBottom, card)
            }
            "A" -> {
                // Ace Master Royal Pip with Sunburst Filigree
                renderAceMasterPip(canvas, w.toFloat(), cx, cy, card)
            }
            else -> {
                // Standard Casino Pip Patterns for 2 to 10
                renderNumberedCardPips(canvas, w.toFloat(), cx, cy, innerLeft, innerTop, innerRight, innerBottom, card)
            }
        }

        return bitmap
    }

    // ── Royal Court Illustration for K, Q, J ────────────────────────────────────

    private fun renderRoyalCourtIllustration(
        canvas: Canvas,
        w: Float,
        cx: Float,
        cy: Float,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        card: VoiceCardExtractor.PlayingCard
    ) {
        val courtRect = RectF(left, top, right, bottom)
        val courtRadius = w * 0.02f

        // 1. Ornate Golden Portrait Frame
        val frameBgPaint = Paint().apply {
            isAntiAlias = true
            shader = LinearGradient(
                left, top, right, bottom,
                intArrayOf(Color.parseColor("#FFFBEA"), Color.parseColor("#FEF08A"), Color.parseColor("#CA8A04")),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(courtRect, courtRadius, courtRadius, frameBgPaint)

        // Golden Dual Frame Stroke
        val strokePaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = w * 0.005f
            color = Color.parseColor("#78350F")
        }
        canvas.drawRoundRect(courtRect, courtRadius, courtRadius, strokePaint)

        // Inner Medallion Background
        val innerMargin = w * 0.015f
        val medallionRect = RectF(left + innerMargin, top + innerMargin, right - innerMargin, bottom - innerMargin)
        val medallionPaint = Paint().apply {
            isAntiAlias = true
            shader = LinearGradient(
                medallionRect.left, medallionRect.top, medallionRect.right, medallionRect.bottom,
                intArrayOf(Color.parseColor("#1E1B4B"), Color.parseColor("#311042"), Color.parseColor("#0F172A")),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(medallionRect, courtRadius * 0.8f, courtRadius * 0.8f, medallionPaint)

        // 2. Royal Crown at Top
        val crownPaint = Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            textSize = w * 0.12f
        }
        canvas.drawText("👑", cx, cy - (w * 0.15f), crownPaint)

        // 3. Royal Title & Badge
        val titleText = when (card.rank) {
            "K" -> "KING"
            "Q" -> "QUEEN"
            "J" -> "JACK"
            else -> card.rank
        }

        val badgePaint = Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("serif", Typeface.BOLD)
            textSize = w * 0.052f
            letterSpacing = 0.20f
            shader = LinearGradient(
                cx, cy - (w * 0.06f), cx, cy,
                Color.parseColor("#FFFDE7"),
                Color.parseColor("#FFD700"),
                Shader.TileMode.CLAMP
            )
            setShadowLayer(w * 0.01f, 0f, 0f, Color.parseColor("#FFD700"))
        }
        canvas.drawText(titleText, cx, cy - (w * 0.05f), badgePaint)

        // 4. Large 3D Embossed Suit Symbol in Medallion Center
        val suitColor = if (card.isRed) Color.parseColor("#DC2626") else Color.parseColor("#F8FAFC")
        val mainSuitPaint = Paint().apply {
            isAntiAlias = true
            color = suitColor
            textAlign = Paint.Align.CENTER
            textSize = w * 0.22f
            if (card.isRed) {
                setShadowLayer(w * 0.025f, 0f, 0f, Color.parseColor("#B91C1C"))
            } else {
                setShadowLayer(w * 0.025f, 0f, 0f, Color.parseColor("#38BDF8"))
            }
        }
        canvas.drawText(card.symbol, cx, cy + (w * 0.14f), mainSuitPaint)

        // 5. Symmetric Filigree Divider
        val dividerPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = w * 0.0025f
            color = Color.parseColor("#CA8A04")
        }
        canvas.drawLine(left + innerMargin * 2, cy + (w * 0.22f), right - innerMargin * 2, cy + (w * 0.22f), dividerPaint)
    }

    // ── Ace Master Royal Pip ───────────────────────────────────────────────────

    private fun renderAceMasterPip(
        canvas: Canvas,
        w: Float,
        cx: Float,
        cy: Float,
        card: VoiceCardExtractor.PlayingCard
    ) {
        // 1. Radiant Golden Sunburst Filigree Rays
        val rayPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = w * 0.0018f
            color = Color.parseColor("#40B45309")
        }
        val rayCount = 16
        for (i in 0 until rayCount) {
            val angle = (i * (360.0 / rayCount) * Math.PI / 180.0)
            val r1 = w * 0.15f
            val r2 = w * 0.26f
            val x1 = cx + (r1 * Math.cos(angle)).toFloat()
            val y1 = cy + (r1 * Math.sin(angle)).toFloat()
            val x2 = cx + (r2 * Math.cos(angle)).toFloat()
            val y2 = cy + (r2 * Math.sin(angle)).toFloat()
            canvas.drawLine(x1, y1, x2, y2, rayPaint)
        }

        // 2. Central Ornate Ring
        val ringPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = w * 0.003f
            color = Color.parseColor("#80B45309")
        }
        canvas.drawCircle(cx, cy, w * 0.22f, ringPaint)

        // 3. Giant Master Ace Pip
        val suitColor = if (card.isRed) Color.parseColor("#B91C1C") else Color.parseColor("#0F172A")
        val acePipPaint = Paint().apply {
            isAntiAlias = true
            color = suitColor
            textAlign = Paint.Align.CENTER
            textSize = w * 0.38f
            setShadowLayer(w * 0.02f, 0f, w * 0.005f, Color.parseColor("#60000000"))
        }
        canvas.drawText(card.symbol, cx, cy + (w * 0.13f), acePipPaint)
    }

    // ── Numbered Card Authentic Casino Pips (2 to 10) ──────────────────────────

    private fun renderNumberedCardPips(
        canvas: Canvas,
        w: Float,
        cx: Float,
        cy: Float,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        card: VoiceCardExtractor.PlayingCard
    ) {
        val numVal = card.rank.toIntOrNull() ?: 2
        val suitColor = if (card.isRed) Color.parseColor("#B91C1C") else Color.parseColor("#0F172A")

        val pipPaint = Paint().apply {
            isAntiAlias = true
            color = suitColor
            textAlign = Paint.Align.CENTER
            textSize = w * 0.115f
            setShadowLayer(w * 0.01f, 0f, w * 0.003f, Color.parseColor("#40000000"))
        }

        val colL = left + (w * 0.06f)
        val colR = right - (w * 0.06f)
        val colC = cx

        val row1 = top + (w * 0.06f)
        val row2 = top + (w * 0.17f)
        val row3 = cy
        val row4 = bottom - (w * 0.17f)
        val row5 = bottom - (w * 0.06f)

        // Authentic casino pip coordinates according to standard deck layout
        val coords = when (numVal) {
            2 -> listOf(Pair(colC, row1), Pair(colC, row5))
            3 -> listOf(Pair(colC, row1), Pair(colC, row3), Pair(colC, row5))
            4 -> listOf(Pair(colL, row1), Pair(colR, row1), Pair(colL, row5), Pair(colR, row5))
            5 -> listOf(Pair(colL, row1), Pair(colR, row1), Pair(colC, row3), Pair(colL, row5), Pair(colR, row5))
            6 -> listOf(
                Pair(colL, row1), Pair(colR, row1),
                Pair(colL, row3), Pair(colR, row3),
                Pair(colL, row5), Pair(colR, row5)
            )
            7 -> listOf(
                Pair(colL, row1), Pair(colR, row1),
                Pair(colC, (row1 + row3) / 2f),
                Pair(colL, row3), Pair(colR, row3),
                Pair(colL, row5), Pair(colR, row5)
            )
            8 -> listOf(
                Pair(colL, row1), Pair(colR, row1),
                Pair(colC, (row1 + row3) / 2f),
                Pair(colL, row3), Pair(colR, row3),
                Pair(colC, (row3 + row5) / 2f),
                Pair(colL, row5), Pair(colR, row5)
            )
            9 -> listOf(
                Pair(colL, row1), Pair(colR, row1),
                Pair(colL, row2), Pair(colR, row2),
                Pair(colC, row3),
                Pair(colL, row4), Pair(colR, row4),
                Pair(colL, row5), Pair(colR, row5)
            )
            10 -> listOf(
                Pair(colL, row1), Pair(colR, row1),
                Pair(colC, (row1 + row2) / 2f),
                Pair(colL, row2), Pair(colR, row2),
                Pair(colL, row4), Pair(colR, row4),
                Pair(colC, (row4 + row5) / 2f),
                Pair(colL, row5), Pair(colR, row5)
            )
            else -> listOf(Pair(colC, cy))
        }

        for (pt in coords) {
            canvas.drawText(card.symbol, pt.first, pt.second + (w * 0.038f), pipPaint)
        }
    }
}
