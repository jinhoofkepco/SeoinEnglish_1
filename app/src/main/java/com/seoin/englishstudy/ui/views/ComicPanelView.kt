package com.seoin.englishstudy.ui.views

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import com.seoin.englishstudy.R
import com.seoin.englishstudy.model.VocabComicFocusStep
import com.seoin.englishstudy.ui.dsl.color
import com.seoin.englishstudy.ui.dsl.colorWithAlpha
import com.seoin.englishstudy.ui.dsl.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

internal class ComicPanelView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
        isDither = true
    }
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val focusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dp(3).toFloat()
        color = context.color(R.color.skin_mark)
    }
    private val markPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dp(8).toFloat()
        strokeCap = Paint.Cap.ROUND
    }
    private var bitmap: Bitmap? = null
    private var panelCount = 3
    private var panelIndex = 0
    private var layout = "horizontal"
    private var focusStep: VocabComicFocusStep? = null
    private var focusStepKey: String? = null
    private var focusStartedAtMs = 0L
    private var choiceMarkCorrect: Boolean? = null
    private var choiceMarkStartedAtMs = 0L

    fun setComic(source: Bitmap, count: Int, sourceLayout: String) {
        bitmap = source
        panelCount = count.coerceAtLeast(1)
        layout = sourceLayout.ifBlank { "horizontal" }
        invalidate()
    }

    fun setPanel(index: Int) {
        panelIndex = index.coerceIn(0, panelCount - 1)
        invalidate()
    }

    fun setFocusStep(step: VocabComicFocusStep?) {
        val newKey = step?.id
        if (focusStepKey != newKey) {
            focusStepKey = newKey
            focusStartedAtMs = System.currentTimeMillis()
        }
        focusStep = step
        invalidate()
    }

    fun setChoiceMark(correct: Boolean?) {
        choiceMarkCorrect = correct
        choiceMarkStartedAtMs = if (correct == null) 0L else System.currentTimeMillis()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(context.color(R.color.skin_surface_alt))
        val source = bitmap ?: return
        val panelSrc = sourceRectForPanel(source, panelIndex.coerceIn(0, panelCount - 1))
        val src = displayedSourceRect(panelSrc, focusStep).toBitmapRect(source)
        val dst = Rect(0, 0, width, height)
        canvas.drawBitmap(source, src, dst, paint)
        drawFocus(canvas, source, RectF(src))
        drawChoiceMark(canvas)
    }

    private fun sourceRectForPanel(source: Bitmap, index: Int): Rect {
        return if (layout == "vertical") {
            val panelHeight = max(1, source.height / panelCount)
            Rect(0, index * panelHeight, source.width, if (index == panelCount - 1) source.height else (index + 1) * panelHeight)
        } else {
            val panelWidth = max(1, source.width / panelCount)
            Rect(index * panelWidth, 0, if (index == panelCount - 1) source.width else (index + 1) * panelWidth, source.height)
        }
    }

    private fun displayedSourceRect(panelSrc: Rect, step: VocabComicFocusStep?): RectF {
        if (step == null || step.focusMode.equals("camera", ignoreCase = true).not() || step.sourceBox == null || width <= 0 || height <= 0) {
            return RectF(panelSrc)
        }
        val start = RectF(panelSrc)
        val target = cameraTargetRect(panelSrc, step)
        val rawProgress = ((System.currentTimeMillis() - focusStartedAtMs).toFloat() / max(1, step.transitionMs).toFloat()).coerceIn(0f, 1f)
        val eased = rawProgress * rawProgress * (3f - 2f * rawProgress)
        if (rawProgress < 1f) postInvalidateDelayed(16L)
        return RectF(
            lerp(start.left, target.left, eased),
            lerp(start.top, target.top, eased),
            lerp(start.right, target.right, eased),
            lerp(start.bottom, target.bottom, eased)
        )
    }

    private fun cameraTargetRect(panelSrc: Rect, step: VocabComicFocusStep): RectF {
        val sourceBox = step.sourceBox?.toRectF() ?: return RectF(panelSrc)
        val zoom = step.zoomScale.coerceIn(1f, 1.8f)
        val viewAspect = width.toFloat() / max(1f, height.toFloat())
        val panelAspect = panelSrc.width().toFloat() / max(1f, panelSrc.height().toFloat())
        var cropWidth: Float
        var cropHeight: Float
        if (viewAspect >= panelAspect) {
            cropWidth = panelSrc.width().toFloat() / zoom
            cropHeight = cropWidth / viewAspect
        } else {
            cropHeight = panelSrc.height().toFloat() / zoom
            cropWidth = cropHeight * viewAspect
        }
        cropWidth = cropWidth.coerceAtMost(panelSrc.width().toFloat())
        cropHeight = cropHeight.coerceAtMost(panelSrc.height().toFloat())
        val centerX = sourceBox.centerX().coerceIn(panelSrc.left + cropWidth / 2f, panelSrc.right - cropWidth / 2f)
        val centerY = sourceBox.centerY().coerceIn(panelSrc.top + cropHeight / 2f, panelSrc.bottom - cropHeight / 2f)
        return RectF(
            centerX - cropWidth / 2f,
            centerY - cropHeight / 2f,
            centerX + cropWidth / 2f,
            centerY + cropHeight / 2f
        )
    }

    private fun lerp(start: Float, end: Float, progress: Float): Float {
        return start + (end - start) * progress
    }

    private fun RectF.toBitmapRect(source: Bitmap): Rect {
        val leftSafe = left.roundToInt().coerceIn(0, max(0, source.width - 1))
        val topSafe = top.roundToInt().coerceIn(0, max(0, source.height - 1))
        val rightSafe = right.roundToInt().coerceIn(leftSafe + 1, source.width)
        val bottomSafe = bottom.roundToInt().coerceIn(topSafe + 1, source.height)
        return Rect(leftSafe, topSafe, rightSafe, bottomSafe)
    }

    private fun drawFocus(canvas: Canvas, source: Bitmap, displayedSrc: RectF) {
        if (focusStep == null) return
        val focusOnPanel = focusDisplayRect(source, displayedSrc) ?: return
        val phase = (System.currentTimeMillis() % 1400L).toFloat() / 1400f
        val pulse = ((sin(phase * Math.PI * 2.0).toFloat() + 1f) / 2f)
        val halo = RectF(focusOnPanel).apply {
            inset(-context.dp(13).toFloat(), -context.dp(13).toFloat())
        }

        overlayPaint.style = Paint.Style.FILL
        overlayPaint.color = colorWithAlpha(context.color(R.color.skin_mark), (76 + pulse * 100).roundToInt())
        canvas.drawRoundRect(halo, context.dp(18).toFloat(), context.dp(18).toFloat(), overlayPaint)

        val softCenter = RectF(focusOnPanel).apply {
            inset(-context.dp(4).toFloat(), -context.dp(4).toFloat())
        }
        overlayPaint.color = colorWithAlpha(context.color(R.color.skin_surface), (38 + pulse * 54).roundToInt())
        canvas.drawRoundRect(softCenter, context.dp(14).toFloat(), context.dp(14).toFloat(), overlayPaint)

        drawSparkles(canvas, halo, phase)
        postInvalidateDelayed(60L)
    }

    private fun focusDisplayRect(source: Bitmap, displayedSrc: RectF): RectF? {
        val box = focusStep?.sourceBox ?: return null
        val srcFocus = box.toRectF().apply {
            left = left.coerceIn(0f, source.width.toFloat())
            right = right.coerceIn(left + 1f, source.width.toFloat())
            top = top.coerceIn(0f, source.height.toFloat())
            bottom = bottom.coerceIn(top + 1f, source.height.toFloat())
        }
        if (!RectF.intersects(srcFocus, displayedSrc)) return null
        val scaleX = width.toFloat() / max(1f, displayedSrc.width())
        val scaleY = height.toFloat() / max(1f, displayedSrc.height())
        return RectF(
            (srcFocus.left - displayedSrc.left) * scaleX,
            (srcFocus.top - displayedSrc.top) * scaleY,
            (srcFocus.right - displayedSrc.left) * scaleX,
            (srcFocus.bottom - displayedSrc.top) * scaleY
        )
    }

    private fun drawSparkles(canvas: Canvas, rect: RectF, phase: Float) {
        val points = listOf(
            rect.left to rect.top,
            rect.right to rect.top + rect.height() * 0.12f,
            rect.left + rect.width() * 0.18f to rect.bottom,
            rect.right - rect.width() * 0.12f to rect.bottom
        )
        points.forEachIndexed { index, point ->
            val localPhase = ((phase + index * 0.23f) % 1f)
            val alpha = (150 + (1f - abs(localPhase - 0.5f) * 2f) * 105).roundToInt().coerceAtMost(255)
            val size = context.dp(11) + context.dp(8) * (1f - localPhase)
            val x = point.first
            val y = point.second
            focusPaint.style = Paint.Style.STROKE
            focusPaint.strokeWidth = context.dp(3).toFloat()
            focusPaint.color = colorWithAlpha(context.color(R.color.skin_mark), alpha)
            canvas.drawLine(x - size, y, x + size, y, focusPaint)
            canvas.drawLine(x, y - size, x, y + size, focusPaint)
        }
    }

    private fun drawChoiceMark(canvas: Canvas) {
        val correct = choiceMarkCorrect ?: return
        val elapsed = (System.currentTimeMillis() - choiceMarkStartedAtMs).coerceAtLeast(0L)
        val rawProgress = (elapsed.toFloat() / 180f).coerceIn(0f, 1f)
        val popScale = 0.78f + 0.22f * (rawProgress * rawProgress * (3f - 2f * rawProgress))
        val centerX = width / 2f
        val centerY = height / 2f
        val radius = min(width, height) * 0.43f * popScale
        markPaint.color = if (correct) 0xFF34A853.toInt() else 0xFFE53935.toInt()
        markPaint.strokeWidth = context.dp(15).toFloat()
        markPaint.style = Paint.Style.STROKE
        markPaint.strokeCap = Paint.Cap.ROUND
        if (correct) {
            markPaint.color = colorWithAlpha(0xFFFFFFFF.toInt(), 225)
            markPaint.strokeWidth = context.dp(21).toFloat()
            canvas.drawCircle(centerX, centerY, radius, markPaint)
            markPaint.color = 0xFF34A853.toInt()
            markPaint.strokeWidth = context.dp(15).toFloat()
            canvas.drawCircle(centerX, centerY, radius, markPaint)
        } else {
            val left = width * 0.16f
            val right = width * 0.84f
            val top = height * 0.11f
            val bottom = height * 0.89f
            markPaint.color = colorWithAlpha(0xFFFFFFFF.toInt(), 230)
            markPaint.strokeWidth = context.dp(23).toFloat()
            canvas.drawLine(left, top, right, bottom, markPaint)
            canvas.drawLine(right, top, left, bottom, markPaint)
            markPaint.color = 0xFFE53935.toInt()
            markPaint.strokeWidth = context.dp(15).toFloat()
            canvas.drawLine(left, top, right, bottom, markPaint)
            canvas.drawLine(right, top, left, bottom, markPaint)
        }
        if (rawProgress < 1f) postInvalidateDelayed(16L)
    }
}
