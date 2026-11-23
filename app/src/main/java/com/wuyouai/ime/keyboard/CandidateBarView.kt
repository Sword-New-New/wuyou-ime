package com.wuyouai.ime.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.wuyouai.ime.prediction.Candidate
import com.wuyouai.ime.themes.KeyboardTheme
import com.wuyouai.ime.themes.ThemeManager

/**
 * 候选词栏。
 * 只显示候选文字本身（拼音已在输入框里，不再重复堆在候选栏）。
 * 首选词带强调色胶囊背景，整体横向可滑动，右侧「更多」按钮展开全部候选。
 */
class CandidateBarView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var candidates: List<Candidate> = emptyList()
    private var scrollX = 0f
    private var lastTouchX = 0f
    private var isScrolling = false
    private var curIndex = 0

    var onCandidateSelected: ((Candidate) -> Unit)? = null
    var onExpandMore: (() -> Unit)? = null

    private var theme: KeyboardTheme = ThemeManager.byId("pure_white")

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val activeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val activeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val morePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private data class Item(val candidate: Candidate, val rect: RectF)
    private var items: List<Item> = emptyList()

    private val density: Float get() = resources.displayMetrics.density
    private fun dp(v: Float): Float = v * density

    private val moreWidth: Float get() = dp(34f)

    init {
        setBackgroundColor(0x00000000)
    }

    fun setTheme(t: KeyboardTheme) {
        theme = t
        textPaint.color = t.candidateText
        activeTextPaint.color = t.accent
        activeBgPaint.color = withAlpha(t.accent, 0x1F)
        morePaint.color = t.accent
        dividerPaint.color = withAlpha(t.keyText, 0x14)
        invalidate()
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        (alpha shl 24) or (color and 0x00FFFFFF)

    fun setCandidates(list: List<Candidate>) {
        candidates = list
        scrollX = 0f
        layoutItems()
        invalidate()
    }

    private fun textOf(c: Candidate): String = c.text

    private fun layoutItems() {
        val out = mutableListOf<Item>()
        if (height <= 0) {
            items = out
            return
        }
        val textSize = height * 0.42f
        textPaint.textSize = textSize
        if (textSize <= 0f) {
            items = out
            return
        }
        val padH = dp(11f)
        val gap = dp(4f)
        val top = dp(5f)
        val bottom = height - dp(5f)
        var x = dp(6f) + scrollX
        val limit = width - moreWidth

        candidates.forEach { cand ->
            val label = textOf(cand)
            val w = textPaint.measureText(label) + padH * 2
            if (x + w > -w && x < limit) {
                out.add(Item(cand, RectF(x, top, x + w, bottom)))
            }
            x += w + gap
        }
        items = out
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutItems()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(theme.candidateBarBg)

        val radius = dp(7f)
        items.forEachIndexed { idx, it ->
            val isFirst = idx == 0
            if (isFirst) {
                canvas.drawRoundRect(it.rect, radius, radius, activeBgPaint)
            }
            val paint = if (isFirst) activeTextPaint else textPaint
            paint.textSize = height * 0.42f
            paint.textAlign = Paint.Align.CENTER
            val fm = paint.fontMetrics
            val baseline = it.rect.centerY() - (fm.ascent + fm.descent) / 2f
            canvas.drawText(textOf(it.candidate), it.rect.centerX(), baseline, paint)
        }

        // 有候选时才显示右侧展开箭头，避免空栏上出现孤零零的符号
        if (candidates.isNotEmpty()) {
            canvas.drawRect(
                width - moreWidth, dp(9f), width - moreWidth + dp(0.6f), height - dp(9f),
                dividerPaint
            )
            morePaint.textSize = height * 0.34f
            morePaint.textAlign = Paint.Align.CENTER
            val fm = morePaint.fontMetrics
            val baseline = height / 2f - (fm.ascent + fm.descent) / 2f
            canvas.drawText("▾", width - moreWidth / 2f, baseline, morePaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                isScrolling = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastTouchX
                if (kotlin.math.abs(dx) > dp(5f)) {
                    isScrolling = true
                    scrollX += dx
                    if (scrollX > 0f) scrollX = 0f
                    lastTouchX = event.x
                    layoutItems()
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!isScrolling) {
                    val x = event.x
                    val y = event.y
                    items.firstOrNull { it.rect.contains(x, y) }?.let {
                        curIndex = candidates.indexOf(it.candidate)
                        onCandidateSelected?.invoke(it.candidate)
                        return true
                    }
                    if (x > width - moreWidth && candidates.isNotEmpty()) {
                        onExpandMore?.invoke()
                        return true
                    }
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
