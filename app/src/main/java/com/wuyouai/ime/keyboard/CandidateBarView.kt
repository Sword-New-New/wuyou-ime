package com.wuyouai.ime.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.wuyouai.ime.prediction.Candidate
import com.wuyouai.ime.themes.KeyboardTheme
import com.wuyouai.ime.themes.ThemeManager

/**
 * 顶部候选词与快捷工具栏（重设计版）。
 * 有候选词时展示横向可滑动候选条与「更多」箭头；
 * 未输入时展示极简快捷工具栏（布局切换 / 快速换肤 / 收起键盘），消除空置白带。
 */
class CandidateBarView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var candidates: List<Candidate> = emptyList()
    private var scrollX = 0f
    private var lastTouchX = 0f
    private var isScrolling = false
    private var curIndex = 0

    var modeLabel: String = "26键"
        set(v) { field = v; invalidate() }

    var onCandidateSelected: ((Candidate) -> Unit)? = null
    var onExpandMore: (() -> Unit)? = null
    var onToggleLayout: (() -> Unit)? = null
    var onCycleTheme: (() -> Unit)? = null
    var onHideKeyboard: (() -> Unit)? = null

    private var theme: KeyboardTheme = ThemeManager.byId("pure_white")

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val activeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val activeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val morePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hairlinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val iconDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val iconPath = Path()

    private data class Item(val candidate: Candidate, val rect: RectF)
    private var items: List<Item> = emptyList()

    private val modePillRect = RectF()
    private val themeBtnRect = RectF()
    private val hideBtnRect = RectF()

    private val density: Float get() = resources.displayMetrics.density
    private fun dp(v: Float): Float = v * density

    private val moreWidth: Float get() = dp(36f)

    init {
        setBackgroundColor(0x00000000)
    }

    fun setTheme(t: KeyboardTheme) {
        theme = t
        textPaint.color = t.candidateText
        activeTextPaint.color = t.accent
        activeBgPaint.color = withAlpha(t.accent, if (t.isDark) 0x36 else 0x1F)
        morePaint.color = t.accent
        dividerPaint.color = withAlpha(t.keyText, 0x18)
        hairlinePaint.color = t.hairline
        iconPaint.color = withAlpha(t.keyText, 0xA6)
        iconDotPaint.color = withAlpha(t.keyText, 0xA6)
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
        val textSize = height * 0.40f
        textPaint.textSize = textSize
        if (textSize <= 0f) {
            items = out
            return
        }
        val padH = dp(11f)
        val gap = dp(4f)
        val top = dp(6f)
        val bottom = height - dp(6f)
        var x = dp(8f) + scrollX
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

    private fun layoutToolbar() {
        if (width <= 0 || height <= 0) return
        val cy = height / 2f
        val pillH = dp(26f)
        val pillW = dp(56f)
        modePillRect.set(dp(10f), cy - pillH / 2f, dp(10f) + pillW, cy + pillH / 2f)

        val btnSize = dp(34f)
        themeBtnRect.set(
            modePillRect.right + dp(8f),
            cy - btnSize / 2f,
            modePillRect.right + dp(8f) + btnSize,
            cy + btnSize / 2f
        )

        hideBtnRect.set(
            width - dp(8f) - btnSize,
            cy - btnSize / 2f,
            width - dp(8f),
            cy + btnSize / 2f
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutItems()
        layoutToolbar()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(theme.candidateBarBg)
        // 顶部 1dp 细分割线，清晰区分宿主应用与键盘区域
        canvas.drawRect(0f, 0f, width.toFloat(), dp(1f), hairlinePaint)

        if (candidates.isNotEmpty()) {
            drawCandidates(canvas)
        } else {
            drawToolbar(canvas)
        }
    }

    private fun drawCandidates(canvas: Canvas) {
        val radius = dp(8f)
        items.forEachIndexed { idx, it ->
            val isFirst = idx == 0
            if (isFirst) {
                canvas.drawRoundRect(it.rect, radius, radius, activeBgPaint)
            }
            val paint = if (isFirst) activeTextPaint else textPaint
            paint.textSize = height * 0.40f
            paint.textAlign = Paint.Align.CENTER
            val fm = paint.fontMetrics
            val baseline = it.rect.centerY() - (fm.ascent + fm.descent) / 2f
            canvas.drawText(textOf(it.candidate), it.rect.centerX(), baseline, paint)
        }

        // 右侧分割线与矢量展开箭头
        canvas.drawRect(
            width - moreWidth, dp(10f), width - moreWidth + dp(0.7f), height - dp(10f),
            dividerPaint
        )
        drawChevronDown(canvas, width - moreWidth / 2f, height / 2f, theme.accent)
    }

    private fun drawToolbar(canvas: Canvas) {
        if (modePillRect.isEmpty) layoutToolbar()

        // 1) 左侧布局模式胶囊按钮（26键 / 九宫格 / EN）
        canvas.drawRoundRect(modePillRect, dp(13f), dp(13f), activeBgPaint)
        activeTextPaint.textSize = dp(12f)
        activeTextPaint.textAlign = Paint.Align.CENTER
        val fm = activeTextPaint.fontMetrics
        val baseline = modePillRect.centerY() - (fm.ascent + fm.descent) / 2f
        canvas.drawText(modeLabel, modePillRect.centerX(), baseline, activeTextPaint)

        // 2) 快速切换主题调色盘图标
        drawPaletteIcon(canvas, themeBtnRect.centerX(), themeBtnRect.centerY())

        // 3) 最右侧收起键盘箭头
        drawChevronDown(canvas, hideBtnRect.centerX(), hideBtnRect.centerY(), withAlpha(theme.keyText, 0xA6))
    }

    private fun drawPaletteIcon(canvas: Canvas, cx: Float, cy: Float) {
        iconPaint.color = withAlpha(theme.keyText, 0xA6)
        iconPaint.strokeWidth = dp(1.6f)
        val r = dp(7.8f)
        canvas.drawCircle(cx, cy, r, iconPaint)
        val dr = dp(1.25f)
        canvas.drawCircle(cx - dp(2.8f), cy - dp(2.2f), dr, iconDotPaint)
        canvas.drawCircle(cx + dp(2.8f), cy - dp(2.2f), dr, iconDotPaint)
        canvas.drawCircle(cx - dp(2.2f), cy + dp(2.8f), dr, iconDotPaint)
    }

    private fun drawChevronDown(canvas: Canvas, cx: Float, cy: Float, color: Int) {
        iconPaint.color = color
        iconPaint.strokeWidth = dp(1.8f)
        val w = dp(5.2f)
        val h = dp(3.0f)
        iconPath.reset()
        iconPath.moveTo(cx - w, cy - h * 0.6f)
        iconPath.lineTo(cx, cy + h)
        iconPath.lineTo(cx + w, cy - h * 0.6f)
        canvas.drawPath(iconPath, iconPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                isScrolling = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (candidates.isNotEmpty()) {
                    val dx = event.x - lastTouchX
                    if (kotlin.math.abs(dx) > dp(5f)) {
                        isScrolling = true
                        scrollX += dx
                        if (scrollX > 0f) scrollX = 0f
                        lastTouchX = event.x
                        layoutItems()
                        invalidate()
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!isScrolling) {
                    val x = event.x
                    val y = event.y
                    if (candidates.isNotEmpty()) {
                        items.firstOrNull { it.rect.contains(x, y) }?.let {
                            curIndex = candidates.indexOf(it.candidate)
                            onCandidateSelected?.invoke(it.candidate)
                            return true
                        }
                        if (x > width - moreWidth) {
                            onExpandMore?.invoke()
                            return true
                        }
                    } else {
                        if (modePillRect.contains(x, y)) {
                            onToggleLayout?.invoke()
                            return true
                        }
                        if (themeBtnRect.contains(x, y)) {
                            onCycleTheme?.invoke()
                            return true
                        }
                        if (hideBtnRect.contains(x, y)) {
                            onHideKeyboard?.invoke()
                            return true
                        }
                    }
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
