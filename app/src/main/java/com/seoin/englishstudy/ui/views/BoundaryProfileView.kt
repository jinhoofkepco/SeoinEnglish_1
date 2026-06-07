package com.seoin.englishstudy.ui.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import com.seoin.englishstudy.R
import com.seoin.englishstudy.model.AdjustSelection
import com.seoin.englishstudy.model.AudioProfile
import com.seoin.englishstudy.ui.dsl.color
import com.seoin.englishstudy.ui.dsl.dp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal class BoundaryProfileView(
    context: Context,
    private val editsStart: Boolean,
    private val waveformOffsetMs: () -> Int,
    private val activeAdjustSelection: () -> AdjustSelection?,
    private val setBoundaryToTime: (AdjustSelection, Boolean, Int, Boolean) -> Unit,
    private val formatTimeMs: (Int) -> String
) : View(context) {
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = context.dp(11).toFloat()
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private var profile: AudioProfile? = null
    private var label: String = ""
    private var centerMs: Int = 0
    private var candidateMs: Int? = null
    private var lowerMs: Int = 0
    private var upperMs: Int = 0
    private var displayStartMs: Int = 0
    private var displayEndMs: Int = 0

    fun setBoundary(profile: AudioProfile?, label: String, centerMs: Int, candidateMs: Int?, lowerMs: Int, upperMs: Int) {
        this.profile = profile
        this.label = label
        this.centerMs = centerMs
        this.candidateMs = candidateMs
        this.lowerMs = lowerMs
        this.upperMs = upperMs
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (profile == null || displayEndMs <= displayStartMs) return true
        parent?.requestDisallowInterceptTouchEvent(true)
        val clampedX = event.x.coerceIn(0f, width.toFloat())
        val touchedProfileMs = (displayStartMs + (clampedX / max(1f, width.toFloat()) * (displayEndMs - displayStartMs))).roundToInt()
        val touchedMs = touchedProfileMs - waveformOffsetMs()
        val selection = activeAdjustSelection() ?: return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                setBoundaryToTime(selection, editsStart, touchedMs, false)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                setBoundaryToTime(selection, editsStart, touchedMs, true)
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val profile = profile
        val width = width.toFloat()
        val height = height.toFloat()
        barPaint.color = context.color(R.color.skin_surface_alt)
        canvas.drawRoundRect(0f, 0f, width, height, context.dp(8).toFloat(), context.dp(8).toFloat(), barPaint)
        textPaint.color = context.color(R.color.skin_muted)
        canvas.drawText(label, context.dp(6).toFloat(), context.dp(14).toFloat(), textPaint)
        if (profile == null || profile.samples.isEmpty()) {
            canvas.drawText("프로파일 준비 중", context.dp(64).toFloat(), context.dp(14).toFloat(), textPaint)
            return
        }

        val offset = waveformOffsetMs()
        val centerProfileMs = centerMs + offset
        val displayStart = max(0, centerProfileMs - 650)
        val displayEnd = min(profile.durationMs, centerProfileMs + 650)
        if (displayEnd <= displayStart) return
        displayStartMs = displayStart
        displayEndMs = displayEnd

        val startIndex = (displayStart / profile.windowMs).coerceIn(0, profile.samples.lastIndex)
        val endIndex = (displayEnd / profile.windowMs).coerceIn(startIndex, profile.samples.lastIndex)
        var localMax = 0.001f
        for (index in startIndex..endIndex) localMax = max(localMax, profile.samples[index])

        val top = context.dp(20).toFloat()
        val bottom = height - context.dp(8)
        val centerY = (top + bottom) / 2f
        val halfHeight = (bottom - top) / 2f
        barPaint.strokeWidth = context.dp(1).toFloat()
        barPaint.color = context.color(R.color.skin_line)
        canvas.drawLine(0f, centerY, width, centerY, barPaint)

        val allowedStart = max(lowerMs + offset, displayStart)
        val allowedEnd = min(upperMs + offset, displayEnd)
        if (allowedEnd > allowedStart) {
            val left = ((allowedStart - displayStart).toFloat() / (displayEnd - displayStart).toFloat()) * width
            val right = ((allowedEnd - displayStart).toFloat() / (displayEnd - displayStart).toFloat()) * width
            barPaint.color = context.color(R.color.skin_highlight)
            barPaint.alpha = 70
            canvas.drawRect(left, top, right, bottom, barPaint)
            barPaint.alpha = 255
        }

        barPaint.color = context.color(R.color.skin_primary_dark)
        barPaint.strokeWidth = context.dp(2).toFloat()
        for (index in startIndex..endIndex) {
            val time = index * profile.windowMs
            val x = ((time - displayStart).toFloat() / (displayEnd - displayStart).toFloat()) * width
            val normalized = (profile.samples[index] / localMax).coerceIn(0f, 1f)
            val barHeight = max(1f, normalized * halfHeight)
            canvas.drawLine(x, centerY - barHeight, x, centerY + barHeight, barPaint)
        }

        markerPaint.strokeWidth = context.dp(2).toFloat()
        markerPaint.color = context.color(R.color.skin_accent)
        drawMarker(canvas, centerMs + offset, displayStart, displayEnd, height, markerPaint)
        candidateMs?.let {
            markerPaint.color = context.color(R.color.skin_primary_dark)
            drawMarker(canvas, it + offset, displayStart, displayEnd, height, markerPaint)
        }

        textPaint.color = context.color(R.color.skin_ink)
        val candidateText = candidateMs?.let { "  후보 ${formatTimeMs(it)}" } ?: ""
        canvas.drawText("${formatTimeMs(centerMs)}$candidateText", context.dp(64).toFloat(), context.dp(14).toFloat(), textPaint)
    }

    private fun drawMarker(canvas: Canvas, timeMs: Int, displayStart: Int, displayEnd: Int, height: Float, paint: Paint) {
        if (timeMs !in displayStart..displayEnd) return
        val x = ((timeMs - displayStart).toFloat() / (displayEnd - displayStart).toFloat()) * width.toFloat()
        canvas.drawLine(x, context.dp(18).toFloat(), x, height - context.dp(4), paint)
    }
}
