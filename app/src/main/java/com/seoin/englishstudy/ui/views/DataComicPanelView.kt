package com.seoin.englishstudy.ui.views

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import com.seoin.englishstudy.R
import com.seoin.englishstudy.model.ComicBubble
import com.seoin.englishstudy.model.ComicPanel
import com.seoin.englishstudy.model.ComicSfx
import com.seoin.englishstudy.model.ComicSprite
import com.seoin.englishstudy.ui.dsl.color
import com.seoin.englishstudy.ui.dsl.colorWithAlpha
import com.seoin.englishstudy.ui.dsl.dp
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
internal class DataComicPanelView(context: Context) : View(context) {
        private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        private val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.LEFT
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = 0xFF2F2925.toInt()
        }
        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = context.dp(3).toFloat()
            color = 0xFF1C1917.toInt()
        }
        private var panel = ComicPanel("", "plain", -1, "", "", false, "", null, emptyList(), "", emptyList(), null)
        private var panelIndex = 0
        private var targetWord = ""
        private var isCurrent = false
        private var showPanelNumber = true
        private var showCaption = true
        private var backgroundSheet: Bitmap? = null
        private var backgroundColumns = 2
        private var backgroundRows = 2
        private val startedAtMs = System.currentTimeMillis()

        fun setPanel(value: ComicPanel, index: Int, highlightWord: String) {
            panel = value
            panelIndex = index
            targetWord = highlightWord
            invalidate()
        }

        fun setCurrent(value: Boolean) {
            isCurrent = value
            invalidate()
        }

        fun setShowPanelNumber(value: Boolean) {
            showPanelNumber = value
            invalidate()
        }

        fun setShowCaption(value: Boolean) {
            showCaption = value
            invalidate()
        }

        fun setBackgroundSheet(source: Bitmap?, columns: Int, rows: Int) {
            backgroundSheet = source
            backgroundColumns = columns.coerceAtLeast(1)
            backgroundRows = rows.coerceAtLeast(1)
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val outer = RectF(context.dp(3).toFloat(), context.dp(3).toFloat(), width - context.dp(6).toFloat(), height - context.dp(6).toFloat())
            val captionHeight = if (!showCaption || panel.caption.isBlank()) 0f else context.dp(58).toFloat()
            val art = RectF(outer.left, outer.top, outer.right, outer.bottom - captionHeight)

            panelPaint.style = Paint.Style.FILL
            panelPaint.color = context.color(R.color.skin_surface_alt)
            canvas.drawRoundRect(outer, context.dp(16).toFloat(), context.dp(16).toFloat(), panelPaint)
            canvas.save()
            val clip = Path().apply { addRoundRect(art, context.dp(16).toFloat(), context.dp(16).toFloat(), Path.Direction.CW) }
            canvas.clipPath(clip)
            applyPanelCamera(canvas, art)
            val drewImageBackground = drawBackgroundSheet(canvas, art, clipToRounded = false)
            if (!drewImageBackground) {
                panelPaint.shader = comicBackground(panel.bg, art)
                canvas.drawRect(art, panelPaint)
            }
            panelPaint.shader = null
            if (!drewImageBackground) drawHalftone(canvas, art)
            drawMoodGrade(canvas, art)
            if (panel.fx == "focus") drawFocusLines(canvas, art)
            if (panel.fx == "speed") drawSpeedLines(canvas, art)
            panel.sprites.forEachIndexed { index, sprite -> drawComicSprite(canvas, art, sprite, index) }
            panel.sfx.forEach { drawComicSfx(canvas, art, it) }
            drawComicBubble(canvas, art)
            canvas.restore()

            if (showCaption && panel.caption.isNotBlank()) {
                val captionRect = RectF(outer.left, art.bottom, outer.right, outer.bottom)
                panelPaint.style = Paint.Style.FILL
                panelPaint.color = if (panel.bg == "night") 0xFF292524.toInt() else 0xFFFFFBEB.toInt()
                canvas.drawRect(captionRect, panelPaint)
                panelPaint.style = Paint.Style.STROKE
                panelPaint.strokeWidth = context.dp(3).toFloat()
                panelPaint.color = 0xFF1C1917.toInt()
                canvas.drawLine(captionRect.left, captionRect.top, captionRect.right, captionRect.top, panelPaint)
                captionPaint.textSize = context.dp(14).toFloat()
                captionPaint.color = if (panel.bg == "night") 0xFFFFF7D6.toInt() else 0xFF2F2925.toInt()
                drawWrappedText(canvas, panel.caption, captionRect.left + context.dp(10), captionRect.top + context.dp(22), captionRect.width() - context.dp(20), captionPaint, maxLines = 2)
            }

            if (showPanelNumber) drawPanelNumber(canvas, outer)
            borderPaint.strokeWidth = context.dp(if (panel.climax) 5 else if (isCurrent) 5 else 3).toFloat()
            borderPaint.color = if (panel.climax) 0xFF292524.toInt() else if (isCurrent) 0xFFF97316.toInt() else 0xFF1C1917.toInt()
            if (panel.climax) {
                panelPaint.style = Paint.Style.STROKE
                panelPaint.strokeWidth = context.dp(3).toFloat()
                panelPaint.color = 0xFFFBBF24.toInt()
                canvas.drawRoundRect(outer, context.dp(18).toFloat(), context.dp(18).toFloat(), panelPaint)
            }
            canvas.drawRoundRect(outer, context.dp(16).toFloat(), context.dp(16).toFloat(), borderPaint)

            if (panel.sprites.any { it.anim != "none" } || panel.zoom != null || panel.fx == "shake") postInvalidateDelayed(40L)
        }

        private fun applyPanelCamera(canvas: Canvas, art: RectF) {
            val now = System.currentTimeMillis() - startedAtMs
            val elapsed = (now % 9000L).toFloat()
            val phase = (elapsed / 9000f).coerceIn(0f, 1f)
            val wave = sin(phase * Math.PI * 2.0).toFloat()
            if (panel.fx == "shake") {
                val shakePhase = (now % 180L).toFloat() / 180f
                canvas.translate(
                    sin(shakePhase * Math.PI * 2.0).toFloat() * context.dp(8) * 0.2f,
                    sin((shakePhase * Math.PI * 2.0) + 1.7).toFloat() * context.dp(5) * 0.2f
                )
            }
            val zoom = panel.zoom ?: return
            val pivotX = art.left + art.width() * (zoom.originX.coerceIn(0f, 100f) / 100f)
            val pivotY = art.top + art.height() * (zoom.originY.coerceIn(0f, 100f) / 100f)
            when (zoom.type) {
                "pushin" -> {
                    val p = ((System.currentTimeMillis() - startedAtMs) % 5000L).toFloat() / 5000f
                    val scale = 1f + (zoom.scale.coerceIn(1f, 1.8f) - 1f) * p
                    canvas.scale(scale, scale, pivotX, pivotY)
                }
                "pan" -> {
                    canvas.scale(1.3f, 1.3f, pivotX, pivotY)
                    canvas.translate(wave * art.width() * 0.06f, 0f)
                }
                "kenburns" -> {
                    val p = ((wave + 1f) / 2f)
                    val scale = 1.15f + 0.25f * p
                    canvas.scale(scale, scale, pivotX, pivotY)
                    canvas.translate((0.03f - 0.06f * p) * art.width(), (0.02f - 0.05f * p) * art.height())
                }
                "shakezoom" -> {
                    val shakePhase = (now % 180L).toFloat() / 180f
                    val fastWave = sin(shakePhase * Math.PI * 2.0).toFloat()
                    val fastWaveY = sin((shakePhase * Math.PI * 2.0) + 1.4).toFloat()
                    val scale = 1.29f + 0.009f * fastWave
                    canvas.scale(scale, scale, pivotX, pivotY)
                    canvas.translate(fastWave * art.width() * 0.0048f, fastWaveY * art.height() * 0.0036f)
                }
                else -> canvas.scale(zoom.scale.coerceIn(1f, 1.8f), zoom.scale.coerceIn(1f, 1.8f), pivotX, pivotY)
            }
        }

        private fun drawBackgroundSheet(canvas: Canvas, art: RectF, clipToRounded: Boolean = true): Boolean {
            val source = backgroundSheet ?: return false
            val total = backgroundColumns * backgroundRows
            if (total <= 0 || panel.bgFrame < 0) return false
            val frame = panel.bgFrame.coerceIn(0, total - 1)
            val cellWidth = max(1, source.width / backgroundColumns)
            val cellHeight = max(1, source.height / backgroundRows)
            val column = frame % backgroundColumns
            val row = frame / backgroundColumns
            val src = Rect(
                column * cellWidth,
                row * cellHeight,
                if (column == backgroundColumns - 1) source.width else (column + 1) * cellWidth,
                if (row == backgroundRows - 1) source.height else (row + 1) * cellHeight
            )
            if (clipToRounded) {
                val clip = Path().apply {
                    addRoundRect(art, context.dp(16).toFloat(), context.dp(16).toFloat(), Path.Direction.CW)
                }
                canvas.save()
                canvas.clipPath(clip)
                canvas.drawBitmap(source, src, art, panelPaint)
                canvas.restore()
            } else {
                canvas.drawBitmap(source, src, art, panelPaint)
            }
            return true
        }

        private fun drawMoodGrade(canvas: Canvas, art: RectF) {
            val color = when (panel.mood) {
                "red" -> colorWithAlpha(0xFFB91C1C.toInt(), 56)
                "dusk" -> colorWithAlpha(0xFFF59E0B.toInt(), 34)
                "warm" -> colorWithAlpha(0xFFFDE68A.toInt(), 18)
                else -> return
            }
            panelPaint.style = Paint.Style.FILL
            panelPaint.color = color
            canvas.drawRect(art, panelPaint)
        }

        private fun drawSpeedLines(canvas: Canvas, art: RectF) {
            panelPaint.style = Paint.Style.STROKE
            panelPaint.strokeCap = Paint.Cap.ROUND
            panelPaint.strokeWidth = context.dp(3).toFloat()
            panelPaint.color = colorWithAlpha(0xFFFFFFFF.toInt(), 178)
            listOf(18f, 32f, 46f, 60f, 74f).forEachIndexed { index, yPercent ->
                val y = art.top + art.height() * yPercent / 100f
                canvas.drawLine(art.left, y, art.left + art.width() * (0.3f + index * 0.06f), y, panelPaint)
            }
        }

        private fun drawFocusLines(canvas: Canvas, art: RectF) {
            panelPaint.style = Paint.Style.STROKE
            val cx = art.centerX()
            val cy = art.centerY()
            val radius = max(art.width(), art.height())
            for (index in 0 until 24) {
                val angle = (index / 24.0) * Math.PI * 2.0
                panelPaint.strokeWidth = context.dp(if (index % 2 == 0) 3 else 2).toFloat()
                panelPaint.color = colorWithAlpha(0xFFF97316.toInt(), 160)
                canvas.drawLine(cx, cy, cx + cos(angle).toFloat() * radius, cy + sin(angle).toFloat() * radius, panelPaint)
            }
        }

        private fun drawComicSfx(canvas: Canvas, art: RectF, sfx: ComicSfx) {
            val x = art.left + art.width() * (sfx.x.coerceIn(0f, 100f) / 100f)
            val y = art.top + art.height() * (sfx.y.coerceIn(0f, 100f) / 100f)
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = art.width() * (sfx.size.coerceIn(5f, 18f) / 100f)
            val fm = textPaint.fontMetrics
            canvas.save()
            canvas.translate(x, y)
            canvas.rotate(sfx.rotate)
            textPaint.style = Paint.Style.STROKE
            textPaint.strokeWidth = context.dp(3).toFloat()
            textPaint.color = 0xFF292524.toInt()
            canvas.drawText(sfx.text, 0f, -(fm.ascent + fm.descent) / 2f, textPaint)
            textPaint.style = Paint.Style.FILL
            textPaint.color = sfx.color
            canvas.drawText(sfx.text, 0f, -(fm.ascent + fm.descent) / 2f, textPaint)
            canvas.restore()
            textPaint.style = Paint.Style.FILL
        }

        private fun comicBackground(name: String, rect: RectF): Shader? {
            val colors = when (name) {
                "snow" -> intArrayOf(0xFFDBEAFE.toInt(), 0xFFEFF6FF.toInt())
                "sky" -> intArrayOf(0xFFBAE6FD.toInt(), 0xFFF0F9FF.toInt())
                "forest" -> intArrayOf(0xFFBBF7D0.toInt(), 0xFFF0FDF4.toInt())
                "desert" -> intArrayOf(0xFFFDE68A.toInt(), 0xFFFFFBEA.toInt())
                "lava" -> intArrayOf(0xFFFECACA.toInt(), 0xFFFFF7ED.toInt())
                "ocean" -> intArrayOf(0xFF7DD3FC.toInt(), 0xFFE0F2FE.toInt())
                "night" -> intArrayOf(0xFF1E293B.toInt(), 0xFF475569.toInt())
                "room" -> intArrayOf(0xFFFEF3C7.toInt(), 0xFFFFFBEA.toInt())
                "sunset" -> intArrayOf(0xFFFDBA74.toInt(), 0xFFFFF1E6.toInt())
                else -> intArrayOf(0xFFFFF7ED.toInt(), 0xFFFFF7ED.toInt())
            }
            return LinearGradient(0f, rect.top, 0f, rect.bottom, colors[0], colors[1], Shader.TileMode.CLAMP)
        }

        private fun drawHalftone(canvas: Canvas, art: RectF) {
            panelPaint.shader = null
            panelPaint.style = Paint.Style.FILL
            panelPaint.color = colorWithAlpha(0xFF000000.toInt(), 14)
            val step = context.dp(10)
            var y = art.top + context.dp(6)
            while (y < art.bottom) {
                var x = art.left + context.dp(6)
                while (x < art.right) {
                    canvas.drawCircle(x, y, context.dp(1).toFloat(), panelPaint)
                    x += step
                }
                y += step
            }
        }

        private fun drawComicSprite(canvas: Canvas, art: RectF, sprite: ComicSprite, index: Int) {
            val elapsed = ((System.currentTimeMillis() - startedAtMs) % 2400L).toFloat() / 2400f
            val phase = (elapsed + index * 0.17f) % 1f
            val pulse = sin(phase * Math.PI * 2.0).toFloat()
            val scale = sprite.scale.coerceIn(0.5f, 1.6f)
            var x = art.left + art.width() * (sprite.x.coerceIn(13f, 87f) / 100f)
            var y = art.top + art.height() * (sprite.y.coerceIn(18f, 82f) / 100f)
            var rotation = sprite.rotate
            var extraScale = 1f

            when (sprite.anim) {
                "bounce", "float" -> y -= context.dp(if (sprite.anim == "bounce") 12 else 8) * ((pulse + 1f) / 2f)
                "shiver" -> {
                    x += pulse * context.dp(3)
                    rotation += pulse * 5f
                }
                "dash" -> x += pulse * context.dp(12)
                "roll", "spin" -> rotation += phase * 360f
                "jump" -> {
                    y -= max(0f, pulse) * context.dp(20)
                    extraScale = 1f + max(0f, pulse) * 0.08f
                }
                "sway" -> rotation += pulse * 7f
            }

            textPaint.textSize = width * 0.15f * scale
            textPaint.color = if (panel.bg == "night") 0xFFFFFFFF.toInt() else 0xFF1C1917.toInt()
            val label = sprite.char.ifBlank { "?" }
            val fm = textPaint.fontMetrics
            canvas.save()
            canvas.translate(x, y)
            canvas.rotate(rotation)
            canvas.scale(if (sprite.flip) -extraScale else extraScale, extraScale)
            canvas.drawText(label, 0f, -(fm.ascent + fm.descent) / 2f, textPaint)
            canvas.restore()
        }

        private fun drawComicBubble(canvas: Canvas, art: RectF) {
            val bubble = panel.bubble ?: return
            val anchor = panel.sprites.getOrNull(bubble.anchor)
            if (anchor == null && (bubble.x < 0f || bubble.y < 0f)) return
            val bubbleX = if (bubble.x >= 0f) bubble.x else anchor?.x?.coerceIn(18f, 82f) ?: 50f
            val bubbleY = if (bubble.y >= 0f) bubble.y else ((anchor?.y?.coerceIn(18f, 82f) ?: 60f) - 28f).coerceAtLeast(8f)
            val x = art.left + art.width() * (bubbleX.coerceIn(12f, 88f) / 100f)
            val y = art.top + art.height() * (bubbleY.coerceIn(6f, 82f) / 100f)
            captionPaint.textSize = context.dp(13).toFloat()
            captionPaint.color = 0xFF1C1917.toInt()
            val maxWidth = art.width() * 0.62f
            val lines = wrapLines(bubble.text, maxWidth - context.dp(16), captionPaint).take(2)
            val bubbleWidth = min(maxWidth, max(context.dp(72).toFloat(), lines.maxOfOrNull { captionPaint.measureText(it) } ?: context.dp(72).toFloat()) + context.dp(18))
            val bubbleHeight = context.dp(18) + lines.size * context.dp(16)
            val left = (x - bubbleWidth / 2f).coerceIn(art.left + context.dp(8), art.right - bubbleWidth - context.dp(8))
            val top = y.coerceIn(art.top + context.dp(8), art.bottom - bubbleHeight - context.dp(10))
            val rect = RectF(left, top, left + bubbleWidth, top + bubbleHeight)
            panelPaint.style = Paint.Style.FILL
            panelPaint.color = 0xFFFFFFFF.toInt()
            canvas.drawRoundRect(rect, context.dp(12).toFloat(), context.dp(12).toFloat(), panelPaint)
            panelPaint.style = Paint.Style.STROKE
            panelPaint.strokeWidth = context.dp(2).toFloat()
            panelPaint.color = 0xFF1C1917.toInt()
            canvas.drawRoundRect(rect, context.dp(12).toFloat(), context.dp(12).toFloat(), panelPaint)
            drawWrappedText(canvas, bubble.text, rect.left + context.dp(8), rect.top + context.dp(17), rect.width() - context.dp(16), captionPaint, maxLines = 2)
        }

        private fun drawPanelNumber(canvas: Canvas, outer: RectF) {
            val cx = outer.left + context.dp(18)
            val cy = outer.top + context.dp(18)
            panelPaint.style = Paint.Style.FILL
            panelPaint.color = 0xFF1C1917.toInt()
            canvas.drawCircle(cx, cy, context.dp(13).toFloat(), panelPaint)
            textPaint.textSize = context.dp(13).toFloat()
            textPaint.color = 0xFFFFFFFF.toInt()
            val fm = textPaint.fontMetrics
            canvas.drawText((panelIndex + 1).toString(), cx, cy - (fm.ascent + fm.descent) / 2f, textPaint)
        }

        private fun drawWrappedText(canvas: Canvas, value: String, left: Float, top: Float, maxWidth: Float, paint: Paint, maxLines: Int) {
            val lines = wrapLines(value, maxWidth, paint).take(maxLines)
            lines.forEachIndexed { index, line ->
                canvas.drawText(line, left, top + index * context.dp(16), paint)
            }
        }

        private fun wrapLines(value: String, maxWidth: Float, paint: Paint): List<String> {
            val words = value.split(Regex("\\s+")).filter { it.isNotBlank() }
            if (words.isEmpty()) return emptyList()
            val lines = mutableListOf<String>()
            var line = ""
            words.forEach { word ->
                val candidate = if (line.isBlank()) word else "$line $word"
                if (paint.measureText(candidate) <= maxWidth || line.isBlank()) {
                    line = candidate
                } else {
                    lines.add(line)
                    line = word
                }
            }
            if (line.isNotBlank()) lines.add(line)
            return lines
        }
    }
