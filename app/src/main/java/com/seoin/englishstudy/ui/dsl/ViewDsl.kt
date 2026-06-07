package com.seoin.englishstudy.ui.dsl

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.seoin.englishstudy.R
import kotlin.math.min
import kotlin.math.roundToInt

internal fun Context.text(value: String, sizeSp: Float, textColor: Int, style: Int = Typeface.NORMAL): TextView {
    return TextView(this).apply {
        text = value
        textSize = sizeSp
        setTextColor(textColor)
        typeface = Typeface.create(Typeface.DEFAULT, style)
        includeFontPadding = true
    }
}

internal fun Context.chip(value: String): TextView {
    return text(value, 13f, color(R.color.skin_primary), Typeface.BOLD).apply {
        setPadding(dp(12), dp(7), dp(12), dp(7))
        background = rounded(color(R.color.skin_surface_alt), dp(14), color(R.color.skin_line), dp(1))
    }
}

internal fun Context.pill(value: String): TextView {
    return text(value, 14f, color(R.color.skin_ink), Typeface.BOLD).apply {
        gravity = Gravity.CENTER
        setPadding(dp(14), 0, dp(14), 0)
        background = rounded(color(R.color.skin_surface), dp(16), color(R.color.skin_line), dp(1))
        isClickable = true
        isFocusable = true
    }
}

internal fun Context.iconButton(icon: Int, description: String): ImageButton {
    return ImageButton(this).apply {
        setImageResource(icon)
        contentDescription = description
        background = rounded(color(R.color.skin_surface_alt), dp(16))
        scaleType = ImageView.ScaleType.CENTER
        isClickable = true
        isFocusable = true
    }
}

internal fun rounded(fill: Int, radius: Int, stroke: Int? = null, strokeWidth: Int = 0): GradientDrawable {
    return GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fill)
        cornerRadius = radius.toFloat()
        if (stroke != null && strokeWidth > 0) setStroke(strokeWidth, stroke)
    }
}

internal fun Context.color(id: Int): Int = getColor(id)
internal fun colorWithAlpha(base: Int, alpha: Int): Int = (base and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)
internal fun strengthenColor(value: Int): Int = colorWithAlpha(value, min(255, (value ushr 24) + 55))
internal fun parseColorSafe(value: String, fallback: Int): Int {
    return runCatching { android.graphics.Color.parseColor(value) }.getOrDefault(fallback)
}

internal fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
internal fun fixed(w: Int, h: Int) = LinearLayout.LayoutParams(w, h)
internal fun matchWrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
internal fun wrapWrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
internal fun weightWrap() = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
internal fun matchFrame() = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

internal fun LinearLayout.LayoutParams.withBottom(value: Int): LinearLayout.LayoutParams {
    bottomMargin = value
    return this
}

internal fun LinearLayout.LayoutParams.withTop(value: Int): LinearLayout.LayoutParams {
    topMargin = value
    return this
}

internal fun LinearLayout.LayoutParams.withRightMargin(value: Int): LinearLayout.LayoutParams {
    rightMargin = value
    return this
}
