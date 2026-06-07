package com.seoin.englishstudy.ui.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import com.seoin.englishstudy.R
import com.seoin.englishstudy.model.ManualChunk
import com.seoin.englishstudy.model.WordToken
import com.seoin.englishstudy.ui.dsl.color
import com.seoin.englishstudy.ui.dsl.dp
import kotlin.math.abs
import kotlin.math.max

internal class ManualChunkView(context: Context) : View(context) {
    var onChanged: ((List<ManualChunk>) -> Unit)? = null
    var onCompleted: ((List<ManualChunk>) -> Unit)? = null

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = context.dp(25).toFloat()
        color = context.color(R.color.skin_ink)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = context.dp(13).toFloat()
        color = context.color(R.color.skin_muted)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var sentenceText: String = ""
    private var tokens: List<WordToken> = emptyList()
    private var chunks: MutableList<ManualChunk> = mutableListOf()
    private val wordRects = mutableListOf<RectF>()
    private var draftStart = -1
    private var draftEnd = -1
    private var editingIndex: Int? = null

    fun setSentence(text: String, tokens: List<WordToken>, savedChunks: List<ManualChunk>) {
        sentenceText = text
        this.tokens = tokens
        chunks = savedChunks
            .filter { it.startWord in tokens.indices && it.endWord in tokens.indices && it.startWord <= it.endWord }
            .sortedBy { it.startWord }
            .toMutableList()
        draftStart = nextStartWord()
        draftEnd = draftStart
        editingIndex = null
        invalidate()
    }

    fun clearChunks() {
        chunks.clear()
        draftStart = nextStartWord()
        draftEnd = draftStart
        editingIndex = null
        onChanged?.invoke(currentChunks())
        invalidate()
    }

    fun currentChunks(): List<ManualChunk> = chunks.map { it.copy() }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (tokens.isEmpty()) return true
        parent?.requestDisallowInterceptTouchEvent(true)
        val word = wordAt(event.x, event.y)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val edit = word?.let { touched -> chunks.indexOfFirst { touched in it.startWord..it.endWord } } ?: -1
                if (edit >= 0) {
                    editingIndex = edit
                    draftStart = chunks[edit].startWord
                    draftEnd = max(draftStart, word ?: draftStart)
                } else {
                    editingIndex = null
                    draftStart = nextStartWord()
                    draftEnd = max(draftStart, word ?: draftStart)
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (draftStart >= 0) {
                    draftEnd = max(draftStart, word ?: draftEnd).coerceAtMost(tokens.lastIndex)
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                commitDraft()
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        layoutWords(width)
        bubblePaint.color = context.color(R.color.skin_surface)
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), context.dp(18).toFloat(), context.dp(18).toFloat(), bubblePaint)
        chunks.forEachIndexed { index, chunk ->
            drawBubble(canvas, chunk.startWord, chunk.endWord, if (index % 2 == 0) context.color(R.color.skin_mark) else context.color(R.color.skin_highlight), false)
        }
        if (draftStart in tokens.indices && draftEnd in tokens.indices) {
            drawBubble(canvas, draftStart, draftEnd, context.color(R.color.skin_accent), true)
        }
        tokens.forEachIndexed { index, token ->
            val rect = wordRects.getOrNull(index) ?: return@forEachIndexed
            canvas.drawText(token.text, rect.left + context.dp(10), rect.bottom - context.dp(10), textPaint)
        }
        drawHandle(canvas)
        smallPaint.color = context.color(R.color.skin_muted)
        canvas.drawText("${chunks.size} chunks", context.dp(16).toFloat(), height - context.dp(18).toFloat(), smallPaint)
    }

    private fun layoutWords(viewWidth: Int) {
        wordRects.clear()
        if (viewWidth <= 0) return
        val leftPad = context.dp(18).toFloat()
        val rightPad = context.dp(18).toFloat()
        var x = leftPad
        var y = context.dp(28).toFloat()
        val rowHeight = context.dp(50).toFloat()
        tokens.forEach { token ->
            val wordWidth = textPaint.measureText(token.text) + context.dp(22)
            if (x + wordWidth > viewWidth - rightPad && x > leftPad) {
                x = leftPad
                y += rowHeight
            }
            wordRects.add(RectF(x, y, x + wordWidth, y + context.dp(40)))
            x += wordWidth + context.dp(6)
        }
    }

    private fun drawBubble(canvas: Canvas, start: Int, end: Int, fill: Int, active: Boolean) {
        val safeStart = start.coerceIn(0, max(0, wordRects.lastIndex))
        val safeEnd = end.coerceIn(safeStart, max(safeStart, wordRects.lastIndex))
        bubblePaint.color = fill
        bubblePaint.alpha = if (active) 220 else 185
        for (index in safeStart..safeEnd) {
            val rect = RectF(wordRects[index]).apply { inset(-context.dp(2).toFloat(), -context.dp(2).toFloat()) }
            canvas.drawRoundRect(rect, context.dp(18).toFloat(), context.dp(18).toFloat(), bubblePaint)
            val next = wordRects.getOrNull(index + 1)
            if (next != null && index < safeEnd && abs(next.top - rect.top) < 4f) {
                canvas.drawRect(rect.right - context.dp(12), rect.top, next.left + context.dp(12), rect.bottom, bubblePaint)
            }
        }
        bubblePaint.alpha = 255
    }

    private fun drawHandle(canvas: Canvas) {
        val next = nextStartWord()
        val rect = wordRects.getOrNull(next) ?: return
        bubblePaint.color = context.color(R.color.skin_primary)
        canvas.drawRoundRect(rect.left, rect.top - context.dp(18), rect.left + context.dp(34), rect.top + context.dp(10), context.dp(14).toFloat(), context.dp(14).toFloat(), bubblePaint)
        smallPaint.color = context.color(R.color.skin_surface)
        canvas.drawText("+", rect.left + context.dp(11), rect.top + context.dp(2), smallPaint)
    }

    private fun wordAt(x: Float, y: Float): Int? {
        return wordRects.indexOfFirst { it.contains(x, y) }.takeIf { it >= 0 }
            ?: wordRects.withIndex().minByOrNull { (_, rect) ->
                val cx = (rect.left + rect.right) / 2f
                val cy = (rect.top + rect.bottom) / 2f
                abs(cx - x) + abs(cy - y)
            }?.index
    }

    private fun nextStartWord(): Int {
        return (chunks.maxOfOrNull { it.endWord } ?: -1) + 1
    }

    private fun commitDraft() {
        if (draftStart !in tokens.indices || draftEnd !in tokens.indices) return
        val edit = editingIndex
        if (edit != null && edit in chunks.indices) {
            chunks[edit] = ManualChunk(chunks[edit].startWord, draftEnd)
            fixFollowingChunks(edit)
        } else if (draftStart == nextStartWord()) {
            chunks.add(ManualChunk(draftStart, draftEnd))
        }
        chunks = chunks.filter { it.startWord <= it.endWord && it.startWord in tokens.indices }.toMutableList()
        draftStart = nextStartWord()
        draftEnd = draftStart
        editingIndex = null
        onChanged?.invoke(currentChunks())
        invalidate()
        if (chunks.isNotEmpty() && nextStartWord() > tokens.lastIndex) onCompleted?.invoke(currentChunks())
    }

    private fun fixFollowingChunks(fromIndex: Int) {
        var previousEnd = chunks[fromIndex].endWord
        val fixed = chunks.take(fromIndex + 1).toMutableList()
        for (index in fromIndex + 1 until chunks.size) {
            val old = chunks[index]
            val start = previousEnd + 1
            if (start > tokens.lastIndex) break
            val end = max(start, old.endWord).coerceAtMost(tokens.lastIndex)
            fixed.add(ManualChunk(start, end))
            previousEnd = end
        }
        chunks = fixed
    }
}
