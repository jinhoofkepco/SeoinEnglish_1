package com.seoin.englishstudy.ui.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.Layout
import android.widget.TextView
import com.seoin.englishstudy.R
import com.seoin.englishstudy.model.ComprehensionBubble
import com.seoin.englishstudy.model.ManualChunkBubble
import com.seoin.englishstudy.model.ManualChunkHint
import com.seoin.englishstudy.ui.dsl.color
import com.seoin.englishstudy.ui.dsl.colorWithAlpha
import com.seoin.englishstudy.ui.dsl.dp
import com.seoin.englishstudy.ui.dsl.strengthenColor
import kotlin.math.max
import kotlin.math.min

internal class WavyTextView(context: Context) : TextView(context) {
    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.color(R.color.skin_accent)
        strokeWidth = context.dp(2).toFloat()
        style = Paint.Style.STROKE
    }
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val wavePath = Path()
    private var wavyRanges: List<IntRange> = emptyList()
    private var comprehensionBubbles: List<ComprehensionBubble> = emptyList()
    private var manualChunks: List<ManualChunkBubble> = emptyList()
    private var manualHint: ManualChunkHint? = null

    fun setWavyRanges(ranges: List<IntRange>) {
        wavyRanges = ranges
        invalidate()
    }

    fun setComprehensionBubbles(bubbles: List<ComprehensionBubble>) {
        comprehensionBubbles = bubbles
        invalidate()
    }

    fun setManualChunks(chunks: List<ManualChunkBubble>, hint: ManualChunkHint? = null) {
        manualChunks = chunks
        manualHint = hint
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val textLayout = layout
        if (textLayout != null && comprehensionBubbles.isNotEmpty()) drawComprehensionBubbles(canvas, textLayout)
        super.onDraw(canvas)
        if (textLayout == null) return
        if (manualChunks.isNotEmpty()) {
            drawManualChunks(canvas, textLayout)
            drawManualChunkBoundaries(canvas, textLayout)
        }
        manualHint?.let { drawManualHint(canvas, textLayout, it) }
        if (wavyRanges.isEmpty()) return
        val leftPad = totalPaddingLeft.toFloat()
        val topPad = totalPaddingTop.toFloat()
        wavyRanges.forEach { range ->
            val safeStart = range.first.coerceIn(0, text.length)
            val safeEnd = (range.last + 1).coerceIn(safeStart, text.length)
            if (safeEnd <= safeStart) return@forEach
            val startLine = textLayout.getLineForOffset(safeStart)
            val endLine = textLayout.getLineForOffset(safeEnd)
            for (line in startLine..endLine) {
                val lineStart = textLayout.getLineStart(line)
                val lineEnd = textLayout.getLineEnd(line)
                val partStart = max(safeStart, lineStart)
                val partEnd = min(safeEnd, lineEnd)
                if (partEnd <= partStart) continue
                val x1 = leftPad + textLayout.getPrimaryHorizontal(partStart)
                val x2 = leftPad + textLayout.getPrimaryHorizontal(partEnd)
                val y = topPad + textLayout.getLineBaseline(line) + context.dp(4)
                drawWave(canvas, min(x1, x2), max(x1, x2), y.toFloat())
            }
        }
    }

    private fun drawWave(canvas: Canvas, startX: Float, endX: Float, y: Float) {
        if (endX - startX < context.dp(3)) return
        val step = context.dp(7).toFloat()
        val amp = context.dp(3).toFloat()
        wavePath.reset()
        wavePath.moveTo(startX, y)
        var x = startX
        var up = true
        while (x < endX) {
            val nextX = min(endX, x + step)
            val midX = (x + nextX) / 2f
            wavePath.quadTo(midX, y + if (up) -amp else amp, nextX, y)
            x = nextX
            up = !up
        }
        canvas.drawPath(wavePath, wavePaint)
    }

    private fun drawManualChunks(canvas: Canvas, textLayout: Layout) {
        manualChunks.forEach { chunk ->
            drawRangeParts(textLayout, chunk.range) { rect ->
                val radius = context.dp(14).toFloat()
                val body = RectF(rect).apply {
                    inset(-context.dp(3).toFloat(), -context.dp(1).toFloat())
                }
                bubblePaint.style = Paint.Style.FILL
                bubblePaint.color = colorWithAlpha(context.color(R.color.skin_primary_dark), 20)
                val shadow = RectF(body)
                shadow.offset(0f, context.dp(1).toFloat())
                canvas.drawRoundRect(shadow, radius, radius, bubblePaint)

                bubblePaint.color = chunk.color
                canvas.drawRoundRect(body, radius, radius, bubblePaint)

                bubblePaint.style = Paint.Style.STROKE
                bubblePaint.strokeWidth = context.dp(1).toFloat()
                bubblePaint.color = colorWithAlpha(context.color(R.color.skin_primary_dark), 120)
                canvas.drawRoundRect(body, radius, radius, bubblePaint)
                bubblePaint.style = Paint.Style.FILL

                val gloss = RectF(
                    body.left + context.dp(5),
                    body.top + context.dp(3),
                    body.right - context.dp(5),
                    min(body.bottom, body.top + body.height() * 0.38f)
                )
                if (gloss.width() > context.dp(8) && gloss.height() > context.dp(3)) {
                    bubblePaint.color = colorWithAlpha(context.color(R.color.skin_surface), 72)
                    canvas.drawRoundRect(gloss, radius * 0.72f, radius * 0.72f, bubblePaint)
                }
            }
        }
    }

    private fun drawManualChunkBoundaries(canvas: Canvas, textLayout: Layout) {
        manualChunks.filter { it.boundary }.forEach { chunk ->
            val boundaryOffset = (chunk.range.last + 1).coerceIn(0, text.length)
            if (boundaryOffset <= 0) return@forEach
            val lineOffset = (boundaryOffset - 1).coerceIn(0, text.length)
            val line = textLayout.getLineForOffset(lineOffset)
            val leftPad = totalPaddingLeft.toFloat()
            val topPad = totalPaddingTop.toFloat()
            val x = leftPad + textLayout.getPrimaryHorizontal(boundaryOffset)
            val top = topPad + textLayout.getLineTop(line) + context.dp(4)
            val bottom = topPad + textLayout.getLineBottom(line) - context.dp(4)
            markerPaint.pathEffect = null
            markerPaint.strokeWidth = context.dp(3).toFloat()
            markerPaint.color = colorWithAlpha(context.color(R.color.skin_primary_dark), 210)
            canvas.drawLine(x + context.dp(2), top.toFloat(), x + context.dp(2), bottom.toFloat(), markerPaint)
            markerPaint.strokeWidth = context.dp(1).toFloat()
            markerPaint.color = colorWithAlpha(context.color(R.color.skin_surface), 230)
            canvas.drawLine(x + context.dp(2), top.toFloat(), x + context.dp(2), bottom.toFloat(), markerPaint)
        }
    }

    private fun drawManualHint(canvas: Canvas, textLayout: Layout, hint: ManualChunkHint) {
        markerPaint.style = Paint.Style.STROKE
        markerPaint.strokeWidth = context.dp(2).toFloat()
        markerPaint.pathEffect = DashPathEffect(floatArrayOf(context.dp(4).toFloat(), context.dp(3).toFloat()), 0f)
        markerPaint.color = colorWithAlpha(context.color(R.color.skin_primary_dark), 190)
        drawRangeParts(textLayout, hint.range) { rect ->
            val body = RectF(rect).apply {
                inset(-context.dp(4).toFloat(), -context.dp(3).toFloat())
            }
            canvas.drawRoundRect(body, context.dp(10).toFloat(), context.dp(10).toFloat(), markerPaint)
        }
        markerPaint.pathEffect = null
    }

    private fun drawRangeParts(textLayout: Layout, range: IntRange, block: (RectF) -> Unit) {
        val safeStart = range.first.coerceIn(0, text.length)
        val safeEnd = (range.last + 1).coerceIn(safeStart, text.length)
        if (safeEnd <= safeStart) return
        val leftPad = totalPaddingLeft.toFloat()
        val topPad = totalPaddingTop.toFloat()
        val startLine = textLayout.getLineForOffset(safeStart)
        val endLine = textLayout.getLineForOffset((safeEnd - 1).coerceAtLeast(safeStart))
        for (line in startLine..endLine) {
            val lineStart = textLayout.getLineStart(line)
            val lineEnd = textLayout.getLineEnd(line)
            val visibleEnd = textLayout.getLineVisibleEnd(line)
            val partStart = max(safeStart, lineStart)
            val partEnd = min(safeEnd, lineEnd)
            if (partEnd <= partStart) continue
            val x1 = leftPad + if (partStart <= lineStart) textLayout.getLineLeft(line) else textLayout.getPrimaryHorizontal(partStart)
            val x2 = leftPad + if (partEnd >= visibleEnd) textLayout.getLineRight(line) else textLayout.getPrimaryHorizontal(partEnd)
            val left = min(x1, x2) - context.dp(1)
            val right = max(x1, x2) + context.dp(1)
            val top = topPad + textLayout.getLineTop(line) + context.dp(2)
            val bottom = topPad + textLayout.getLineBottom(line) - context.dp(2)
            block(RectF(left, top.toFloat(), right, bottom.toFloat()))
        }
    }

    private fun drawComprehensionBubbles(canvas: Canvas, textLayout: Layout) {
        val leftPad = totalPaddingLeft.toFloat()
        val topPad = totalPaddingTop.toFloat()
        comprehensionBubbles.forEach { bubble ->
            val safeStart = bubble.range.first.coerceIn(0, text.length)
            val safeEnd = (bubble.range.last + 1).coerceIn(safeStart, text.length)
            if (safeEnd <= safeStart) return@forEach
            val startLine = textLayout.getLineForOffset(safeStart)
            val endLine = textLayout.getLineForOffset(safeEnd)
            for (line in startLine..endLine) {
                val lineStart = textLayout.getLineStart(line)
                val lineEnd = textLayout.getLineEnd(line)
                val visibleEnd = textLayout.getLineVisibleEnd(line)
                val partStart = max(safeStart, lineStart)
                val partEnd = min(safeEnd, lineEnd)
                if (partEnd <= partStart) continue
                val x1 = leftPad + if (partStart <= lineStart) textLayout.getLineLeft(line) else textLayout.getPrimaryHorizontal(partStart)
                val x2 = leftPad + if (partEnd >= visibleEnd) textLayout.getLineRight(line) else textLayout.getPrimaryHorizontal(partEnd)
                val left = min(x1, x2) - context.dp(3)
                val right = max(x1, x2) + context.dp(3)
                val top = topPad + textLayout.getLineTop(line) + context.dp(1)
                val bottom = topPad + textLayout.getLineBottom(line) - context.dp(1)
                val rect = RectF(left, top.toFloat(), right, bottom.toFloat())
                drawBubble(canvas, rect, bubble)
            }
        }
    }

    private fun drawBubble(canvas: Canvas, rect: RectF, bubble: ComprehensionBubble) {
        val radius = context.dp(12).toFloat()
        val shadowOffset = if (bubble.pressed) context.dp(1).toFloat() else context.dp(2).toFloat()
        val bodyRect = RectF(rect).apply {
            inset(-context.dp(1).toFloat(), -context.dp(1).toFloat())
        }

        bubblePaint.style = Paint.Style.FILL
        bubblePaint.color = colorWithAlpha(context.color(R.color.skin_primary_dark), if (bubble.pressed) 48 else 26)
        val shadow = RectF(bodyRect)
        shadow.offset(0f, shadowOffset)
        canvas.drawRoundRect(shadow, radius, radius, bubblePaint)

        bubblePaint.color = if (bubble.pressed) strengthenColor(bubble.color) else bubble.color
        canvas.drawRoundRect(bodyRect, radius, radius, bubblePaint)

        val gloss = RectF(
            bodyRect.left + context.dp(5),
            bodyRect.top + context.dp(4),
            bodyRect.right - context.dp(5),
            min(bodyRect.bottom, bodyRect.top + (bodyRect.height() * 0.42f))
        )
        if (gloss.width() > context.dp(8) && gloss.height() > context.dp(3)) {
            bubblePaint.color = colorWithAlpha(context.color(R.color.skin_surface), if (bubble.pressed) 70 else 105)
            canvas.drawRoundRect(gloss, radius * 0.72f, radius * 0.72f, bubblePaint)
        }

        bubblePaint.style = Paint.Style.STROKE
        bubblePaint.strokeWidth = context.dp(if (bubble.pressed) 2 else 1).toFloat()
        bubblePaint.color = colorWithAlpha(context.color(R.color.skin_primary_dark), if (bubble.pressed) 210 else 145)
        canvas.drawRoundRect(bodyRect, radius, radius, bubblePaint)
    }
}
