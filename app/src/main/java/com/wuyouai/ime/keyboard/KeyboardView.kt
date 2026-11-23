package com.wuyouai.ime.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.wuyouai.ime.settings.SettingsManager
import com.wuyouai.ime.themes.KeyboardTheme

/**
 * 键盘主体视图（重设计 v4）。
 * 1. 按下即震动（ACTION_DOWN），跟手不滞后；
 * 2. 收敛版临边光晕（半径 54dp）：精准点亮按压键边缘 + 紧邻按键的临边，不泛光不糊字；
 * 3. 长按多选气泡：qwertyuiop 长按弹出 [数字 / 大写 / 小写]（如 1 / Q / q），
 *    其他字母键长按弹出 [大写 / 小写]（如 A / a），左右滑动即可切换选中项。
 */
class KeyboardView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class Mode { QWERTY_PINYIN, QWERTY_EN, T9_PINYIN, SYMBOLS, NUMBERS, EMOJI }

    var mode: Mode = Mode.QWERTY_PINYIN
        set(v) { field = v; invalidate() }

    var isShift = false
    var isCapsLock = false

    var onKeyPress: ((String) -> Unit)? = null
    var onSpecialKey: ((SpecialKey) -> Unit)? = null
    var onKeyLongPress: ((String) -> Unit)? = null

    enum class SpecialKey {
        SHIFT, DELETE, ENTER, SPACE, MODE_SWITCH, LANG_SWITCH,
        SYMBOL, NUMBER, BACK, EMOJI, CLIPBOARD, CLEAR_COMP
    }

    private enum class IconKind { SHIFT, DELETE, ENTER, SPACE }

    private var theme = com.wuyouai.ime.themes.ThemeManager.byId("pure_white")
    private val settings = SettingsManager.get(context)

    private val keyBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val specialBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val accentBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val accentPressedPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pressedPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val innerPressShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val darkTopHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    // ---- 收敛版临边光晕画笔 ----
    private val edgeRevealOuterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val edgeRevealCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val keyTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val specialTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val iconFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val popupBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val popupShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val popupBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val popupActiveBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // ---- 临边光晕状态 ----
    private var touchX = -1000f
    private var touchY = -1000f
    private var isTouching = false
    private var glowAlpha = 0f
    private var lastFrameMs = 0L

    // ---- 长按多选气泡状态（1/Q/q 或 A/a） ----
    private var longPressKey: Key? = null
    private var longPressOptions: List<String> = emptyList()
    private var longPressSelectedIndex = 0
    private val longPressBarRect = RectF()
    private var longPressCellRects: List<RectF> = emptyList()

    data class Key(
        val label: String,
        val output: String,
        val code: Int,
        val special: SpecialKey? = null,
        val row: Int,
        val col: Float,
        val width: Float = 1f,
        val hint: String = "",
        val isT9: Boolean = false,
        val pad: Float = 0f,
        val isAccent: Boolean = false,
        val isDualLang: Boolean = false,
        val isCjkPunct: Boolean = false,
        val isPunctRail: Boolean = false
    ) {
        var rect: RectF = RectF()
        var pressed: Boolean = false
    }

    var keys: List<Key> = emptyList()
    private var longPressHandler: Runnable? = null
    private var repeatDelete: Runnable? = null
    private var currentPressed: Key? = null
    private var t9RailRect: RectF? = null

    private val tmpRect = RectF()
    private val tmpShadow = RectF()
    private val tmpBounds = Rect()
    private val iconPath = Path()

    fun setTheme(t: KeyboardTheme) {
        theme = t
        keyBgPaint.color = t.keyBg
        specialBgPaint.color = t.specialKeyBg
        accentBgPaint.color = t.accent
        accentPressedPaint.color = darkenColor(t.accent, 0.84f)
        pressedPaint.color = if (t.isDark) t.pressedBg else darkenColor(t.keyBg, 0.88f)
        innerPressShadowPaint.color = if (t.isDark) 0x33000000 else 0x260F172A
        innerPressShadowPaint.strokeWidth = dp(1.4f)
        keyTextPaint.color = t.keyText
        specialTextPaint.color = t.keyText
        subTextPaint.color = withAlpha(t.keyText, 0x73)
        popupBgPaint.color = t.popupBg
        popupActiveBgPaint.color = t.accent
        popupShadowPaint.color = if (t.isDark) 0x55000000 else 0x260F172A
        popupBorderPaint.color = if (t.isDark) 0x28FFFFFF else 0x16000000
        popupBorderPaint.strokeWidth = dp(0.8f)

        darkTopHighlightPaint.color = 0x14FFFFFF
        darkTopHighlightPaint.strokeWidth = dp(0.8f)

        edgeRevealOuterPaint.strokeWidth = dp(2.8f)
        edgeRevealCorePaint.strokeWidth = dp(1.35f)
        invalidate()
    }

    private val density: Float get() = resources.displayMetrics.density
    private fun dp(v: Float): Float = v * density

    private fun withAlpha(color: Int, alpha: Int): Int =
        ((alpha.coerceIn(0, 255)) shl 24) or (color and 0x00FFFFFF)

    private fun darkenColor(color: Int, factor: Float): Int {
        val a = (color ushr 24) and 0xFF
        val r = (((color shr 16) and 0xFF) * factor).toInt().coerceIn(0, 255)
        val g = (((color shr 8) and 0xFF) * factor).toInt().coerceIn(0, 255)
        val b = ((color and 0xFF) * factor).toInt().coerceIn(0, 255)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun iconKindOf(key: Key): IconKind? = when (key.special) {
        SpecialKey.SHIFT -> IconKind.SHIFT
        SpecialKey.DELETE -> IconKind.DELETE
        SpecialKey.ENTER -> IconKind.ENTER
        SpecialKey.SPACE -> IconKind.SPACE
        else -> null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        buildKeysIfNeeded()
        updateGlowAnimation()

        val radius = dp((settings.cachedKeyRadius.takeIf { it > 0 } ?: 8).toFloat())
        val shadowDy = dp(1.3f)

        canvas.drawColor(theme.bg)
        drawFlatKeys(canvas, radius, shadowDy)

        // 仅在深色主题下启用收敛版临边光晕（浅色主题保持纯净无光晕）
        if (theme.isDark && glowAlpha > 0.01f && touchX >= -100f && touchY >= -100f) {
            drawEdgeRevealGlow(canvas, radius)
        }

        // 长按多选气泡优先于普通单键预览
        if (longPressOptions.isNotEmpty()) {
            drawLongPressPopup(canvas, radius)
        } else {
            currentPressed?.let { key ->
                if (key.special == null && !key.isPunctRail && key.label.length == 1) {
                    drawKeyPopup(canvas, key, radius)
                }
            }
        }
    }

    private fun updateGlowAnimation() {
        val now = SystemClock.uptimeMillis()
        val dt = if (lastFrameMs == 0L) 16f else (now - lastFrameMs).coerceIn(1L, 64L).toFloat()
        lastFrameMs = now

        if (isTouching) {
            glowAlpha = (glowAlpha + dt / 70f).coerceAtMost(1f)
            if (glowAlpha < 1f) postInvalidateOnAnimation()
        } else if (glowAlpha > 0f) {
            glowAlpha = (glowAlpha - dt / 210f).coerceAtLeast(0f)
            if (glowAlpha > 0.005f) postInvalidateOnAnimation()
            else glowAlpha = 0f
        }
    }

    /**
     * 收敛版临边光晕（半径 54dp）：
     * 紧贴按压键整圈边缘，同时只柔和点亮紧邻按键面向指尖的那条临边，不再扩散到隔行远端。
     */
    private fun drawEdgeRevealGlow(canvas: Canvas, radius: Float) {
        val revealRadius = dp(54f)
        val baseGlowColor = if (theme.id == "dark_eye_care") 0xFF9EC088.toInt() else 0xFF8CC2FF.toInt()
        val outerAlpha = (glowAlpha * 48).toInt()
        val coreAlpha = (glowAlpha * 205).toInt()

        edgeRevealOuterPaint.shader = RadialGradient(
            touchX, touchY, revealRadius,
            intArrayOf(
                withAlpha(baseGlowColor, outerAlpha),
                withAlpha(baseGlowColor, (outerAlpha * 0.35f).toInt()),
                0x00000000
            ),
            floatArrayOf(0f, 0.58f, 1f),
            Shader.TileMode.CLAMP
        )
        edgeRevealCorePaint.shader = RadialGradient(
            touchX, touchY, revealRadius,
            intArrayOf(
                withAlpha(baseGlowColor, coreAlpha),
                withAlpha(baseGlowColor, (coreAlpha * 0.38f).toInt()),
                0x00000000
            ),
            floatArrayOf(0f, 0.56f, 1f),
            Shader.TileMode.CLAMP
        )

        val maxDistSq = (revealRadius + dp(24f)) * (revealRadius + dp(24f))

        t9RailRect?.let { rail ->
            val dx = rail.centerX() - touchX
            val dy = rail.centerY() - touchY
            if (dx * dx + dy * dy <= maxDistSq) {
                canvas.drawRoundRect(rail, radius, radius, edgeRevealOuterPaint)
                canvas.drawRoundRect(rail, radius, radius, edgeRevealCorePaint)
            }
        }

        keys.forEach { key ->
            if (key.isPunctRail) return@forEach
            val dx = key.rect.centerX() - touchX
            val dy = key.rect.centerY() - touchY
            if (dx * dx + dy * dy <= maxDistSq) {
                tmpRect.set(key.rect)
                if (key.pressed) tmpRect.offset(0f, dp(0.6f))
                canvas.drawRoundRect(tmpRect, radius, radius, edgeRevealOuterPaint)
                canvas.drawRoundRect(tmpRect, radius, radius, edgeRevealCorePaint)
            }
        }

        edgeRevealOuterPaint.shader = null
        edgeRevealCorePaint.shader = null
    }

    /** 常规主题扁平键帽 */
    private fun drawFlatKeys(canvas: Canvas, radius: Float, shadowDy: Float) {
        t9RailRect?.let { rail ->
            shadowPaint.color = theme.specialShadow
            tmpShadow.set(rail)
            tmpShadow.offset(0f, shadowDy)
            canvas.drawRoundRect(tmpShadow, radius, radius, shadowPaint)
            canvas.drawRoundRect(rail, radius, radius, specialBgPaint)
        }

        keys.forEach { key ->
            if (key.isPunctRail) {
                if (key.pressed) {
                    tmpRect.set(key.rect)
                    tmpRect.inset(dp(2f), dp(1.5f))
                    canvas.drawRoundRect(tmpRect, radius * 0.7f, radius * 0.7f, pressedPaint)
                }
                drawKeyContent(canvas, key)
                return@forEach
            }

            val isSpecial = key.special != null && key.special != SpecialKey.SPACE
            val bodyPaint = when {
                key.isAccent -> if (key.pressed) accentPressedPaint else accentBgPaint
                key.pressed -> if (isSpecial && !theme.isDark) {
                    pressedPaint.color = darkenColor(theme.specialKeyBg, 0.88f)
                    pressedPaint
                } else {
                    pressedPaint.color = if (theme.isDark) theme.pressedBg else darkenColor(theme.keyBg, 0.88f)
                    pressedPaint
                }
                isSpecial -> specialBgPaint
                else -> keyBgPaint
            }

            if (!key.pressed) {
                shadowPaint.color = when {
                    key.isAccent -> 0x38000000
                    isSpecial -> theme.specialShadow
                    else -> theme.keyShadow
                }
                tmpShadow.set(key.rect)
                tmpShadow.offset(0f, shadowDy)
                canvas.drawRoundRect(tmpShadow, radius, radius, shadowPaint)
            }

            tmpRect.set(key.rect)
            if (key.pressed) {
                tmpRect.inset(dp(0.4f), dp(0.3f))
                tmpRect.offset(0f, dp(1.2f))
            }
            canvas.drawRoundRect(tmpRect, radius, radius, bodyPaint)

            // 按下时在键帽内侧叠加柔和凹陷内阴影（浅色与深色模式均有清晰下沉手感）
            if (key.pressed) {
                tmpShadow.set(tmpRect)
                tmpShadow.inset(dp(0.7f), dp(0.7f))
                canvas.drawRoundRect(tmpShadow, (radius - dp(0.6f)).coerceAtLeast(1f), (radius - dp(0.6f)).coerceAtLeast(1f), innerPressShadowPaint)
            }

            if (theme.isDark && !key.isAccent && !key.pressed) {
                canvas.drawLine(
                    tmpRect.left + radius, tmpRect.top + dp(0.6f),
                    tmpRect.right - radius, tmpRect.top + dp(0.6f),
                    darkTopHighlightPaint
                )
            }

            drawKeyContent(canvas, key)
        }
    }

    private fun drawKeyPopup(canvas: Canvas, key: Key, radius: Float) {
        val popW = maxOf(key.rect.width() * 1.26f, dp(40f))
        val popH = maxOf(key.rect.height() * 1.06f, dp(46f))
        val cx = key.rect.centerX().coerceIn(popW / 2f + dp(4f), width - popW / 2f - dp(4f))
        val top = (key.rect.top - popH - dp(6f)).coerceAtLeast(dp(2f))
        tmpRect.set(cx - popW / 2f, top, cx + popW / 2f, top + popH)

        tmpShadow.set(tmpRect)
        tmpShadow.offset(0f, dp(3f))
        canvas.drawRoundRect(tmpShadow, radius * 1.25f, radius * 1.25f, popupShadowPaint)
        canvas.drawRoundRect(tmpRect, radius * 1.25f, radius * 1.25f, popupBgPaint)
        canvas.drawRoundRect(tmpRect, radius * 1.25f, radius * 1.25f, popupBorderPaint)

        val label = displayLabelOf(key)
        keyTextPaint.color = theme.keyText
        keyTextPaint.textSize = dp(23f)
        val fm = keyTextPaint.fontMetrics
        val baseline = tmpRect.centerY() - (fm.ascent + fm.descent) / 2f
        canvas.drawText(label, tmpRect.centerX(), baseline, keyTextPaint)
    }

    /** 绘制长按多选气泡（如 1 / Q / q 或 A / a） */
    private fun drawLongPressPopup(canvas: Canvas, radius: Float) {
        if (longPressOptions.isEmpty() || longPressCellRects.isEmpty()) return
        val popRadius = radius * 1.25f

        tmpShadow.set(longPressBarRect)
        tmpShadow.offset(0f, dp(3.5f))
        canvas.drawRoundRect(tmpShadow, popRadius, popRadius, popupShadowPaint)
        canvas.drawRoundRect(longPressBarRect, popRadius, popRadius, popupBgPaint)
        canvas.drawRoundRect(longPressBarRect, popRadius, popRadius, popupBorderPaint)

        val cellRadius = radius * 0.9f
        longPressOptions.forEachIndexed { idx, opt ->
            val cell = longPressCellRects[idx]
            val selected = idx == longPressSelectedIndex
            if (selected) {
                tmpRect.set(cell)
                tmpRect.inset(dp(3f), dp(3f))
                canvas.drawRoundRect(tmpRect, cellRadius, cellRadius, popupActiveBgPaint)
            }
            specialTextPaint.color = if (selected) theme.accentText else theme.keyText
            specialTextPaint.textAlign = Paint.Align.CENTER
            specialTextPaint.textSize = dp(18f)
            val fm = specialTextPaint.fontMetrics
            val baseline = cell.centerY() - (fm.ascent + fm.descent) / 2f
            canvas.drawText(opt, cell.centerX(), baseline, specialTextPaint)
        }
    }

    private fun buildLongPressOptions(key: Key): List<String> {
        if (key.special != null || key.isT9 || key.isPunctRail) return emptyList()
        if (key.output.length == 1 && key.output[0] in 'a'..'z') {
            val lower = key.output
            val upper = lower.uppercase()
            return if (key.hint.length == 1 && key.hint[0] in '0'..'9') {
                listOf(key.hint, upper, lower)
            } else {
                listOf(upper, lower)
            }
        }
        return emptyList()
    }

    private fun openLongPressPopup(key: Key, options: List<String>) {
        longPressKey = key
        longPressOptions = options
        longPressSelectedIndex = 0

        val cellW = dp(40f)
        val pad = dp(4f)
        val totalW = cellW * options.size + pad * 2f
        val totalH = dp(46f)
        val left = (key.rect.centerX() - totalW / 2f).coerceIn(dp(4f), (width - totalW - dp(4f)).coerceAtLeast(dp(4f)))
        val top = (key.rect.top - totalH - dp(8f)).coerceAtLeast(dp(2f))
        longPressBarRect.set(left, top, left + totalW, top + totalH)

        longPressCellRects = options.indices.map { i ->
            val cx0 = left + pad + i * cellW
            RectF(cx0, top + pad, cx0 + cellW, top + totalH - pad)
        }
    }

    private fun dismissLongPressPopup() {
        longPressKey = null
        longPressOptions = emptyList()
        longPressCellRects = emptyList()
        longPressSelectedIndex = 0
    }

    private fun displayLabelOf(key: Key): String = when {
        (mode == Mode.QWERTY_EN) && (isShift || isCapsLock) &&
            key.output.length == 1 && key.output[0] in 'a'..'z' -> key.output.uppercase()
        else -> key.label
    }

    private fun drawKeyContent(canvas: Canvas, key: Key) {
        val fgColor = if (key.isAccent && !key.pressed) theme.accentText else theme.keyText
        val iconKind = iconKindOf(key)
        if (iconKind != null) {
            drawVectorIcon(canvas, key.rect, iconKind, fgColor)
            return
        }

        if (key.isDualLang) {
            drawDualLangLabel(canvas, key.rect, fgColor)
            return
        }

        val keyH = key.rect.height()
        val labelText = displayLabelOf(key)

        if (key.hint.isNotEmpty() && !key.isT9) {
            subTextPaint.textAlign = Paint.Align.RIGHT
            subTextPaint.color = withAlpha(fgColor, 0x6E)
            subTextPaint.textSize = dp(9f)
            canvas.drawText(
                key.hint,
                key.rect.right - dp(4.5f),
                key.rect.top + dp(10.5f),
                subTextPaint
            )
        }

        val isSpecial = key.special != null
        val paint = if (isSpecial || key.isT9) specialTextPaint else keyTextPaint
        paint.color = fgColor
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = when {
            key.isPunctRail -> minOf(keyH * 0.44f, dp(16f))
            isSpecial -> minOf(keyH * 0.30f, key.rect.width() * 0.34f).coerceIn(dp(11.5f), dp(14.5f))
            key.isT9 -> minOf(keyH * 0.32f, dp(15.5f))
            else -> minOf(keyH * 0.44f, dp(21f))
        }

        if (key.isCjkPunct) {
            paint.getTextBounds(labelText, 0, labelText.length, tmpBounds)
            val drawX = key.rect.centerX() - tmpBounds.exactCenterX()
            val drawY = key.rect.centerY() - tmpBounds.exactCenterY()
            canvas.drawText(labelText, drawX, drawY, paint)
            return
        }

        val fm = paint.fontMetrics
        val yOffset = if (key.isT9 && key.hint.isNotEmpty()) -dp(4.5f) else 0f
        val baseline = key.rect.centerY() + yOffset - (fm.ascent + fm.descent) / 2f
        canvas.drawText(labelText, key.rect.centerX(), baseline, paint)

        if (key.isT9 && key.hint.isNotEmpty()) {
            subTextPaint.textAlign = Paint.Align.CENTER
            subTextPaint.color = withAlpha(fgColor, 0x8C)
            subTextPaint.textSize = dp(9.5f)
            val sfm = subTextPaint.fontMetrics
            val subY = key.rect.centerY() + dp(10.5f) - (sfm.ascent + sfm.descent) / 2f
            canvas.drawText(key.hint, key.rect.centerX(), subY, subTextPaint)
        }
    }

    private fun drawDualLangLabel(canvas: Canvas, rect: RectF, color: Int) {
        val primary = if (mode == Mode.QWERTY_EN) "英" else "中"
        val secondary = if (mode == Mode.QWERTY_EN) "/中" else "/英"

        specialTextPaint.color = color
        specialTextPaint.textAlign = Paint.Align.LEFT
        specialTextPaint.textSize = dp(14f)
        val w1 = specialTextPaint.measureText(primary)

        subTextPaint.color = withAlpha(color, 0x85)
        subTextPaint.textAlign = Paint.Align.LEFT
        subTextPaint.textSize = dp(10f)
        val w2 = subTextPaint.measureText(secondary)

        val startX = rect.centerX() - (w1 + w2) / 2f
        val fm = specialTextPaint.fontMetrics
        val baseline = rect.centerY() - (fm.ascent + fm.descent) / 2f
        canvas.drawText(primary, startX, baseline, specialTextPaint)
        canvas.drawText(secondary, startX + w1, baseline, subTextPaint)
        specialTextPaint.textAlign = Paint.Align.CENTER
    }

    private fun drawVectorIcon(canvas: Canvas, rect: RectF, kind: IconKind, color: Int) {
        val cx = rect.centerX()
        val cy = rect.centerY()
        iconPaint.color = color
        iconPaint.strokeWidth = dp(1.8f)
        iconFillPaint.color = color
        iconPath.reset()

        when (kind) {
            IconKind.SHIFT -> {
                val s = dp(8.5f)
                iconPath.moveTo(cx, cy - s)
                iconPath.lineTo(cx - s * 0.95f, cy + s * 0.05f)
                iconPath.lineTo(cx - s * 0.42f, cy + s * 0.05f)
                iconPath.lineTo(cx - s * 0.42f, cy + s * 0.85f)
                iconPath.lineTo(cx + s * 0.42f, cy + s * 0.85f)
                iconPath.lineTo(cx + s * 0.42f, cy + s * 0.05f)
                iconPath.lineTo(cx + s * 0.95f, cy + s * 0.05f)
                iconPath.close()
                if (isShift || isCapsLock) {
                    canvas.drawPath(iconPath, iconFillPaint)
                }
                canvas.drawPath(iconPath, iconPaint)
            }
            IconKind.DELETE -> {
                val w = dp(10f)
                val h = dp(6.5f)
                iconPath.moveTo(cx - w, cy)
                iconPath.lineTo(cx - w * 0.42f, cy - h)
                iconPath.lineTo(cx + w * 0.85f, cy - h)
                iconPath.lineTo(cx + w * 0.85f, cy + h)
                iconPath.lineTo(cx - w * 0.42f, cy + h)
                iconPath.close()
                canvas.drawPath(iconPath, iconPaint)
                val xOff = dp(2.2f)
                val xSize = dp(2.8f)
                canvas.drawLine(cx + xOff - xSize, cy - xSize, cx + xOff + xSize, cy + xSize, iconPaint)
                canvas.drawLine(cx + xOff + xSize, cy - xSize, cx + xOff - xSize, cy + xSize, iconPaint)
            }
            IconKind.ENTER -> {
                val w = dp(8.5f)
                val h = dp(5f)
                iconPaint.strokeWidth = dp(2.0f)
                iconPath.moveTo(cx + w * 0.85f, cy - h)
                iconPath.lineTo(cx + w * 0.85f, cy + h * 0.35f)
                iconPath.lineTo(cx - w * 0.65f, cy + h * 0.35f)
                canvas.drawPath(iconPath, iconPaint)

                iconPath.reset()
                iconPath.moveTo(cx - w * 0.25f, cy - h * 0.45f)
                iconPath.lineTo(cx - w * 0.78f, cy + h * 0.35f)
                iconPath.lineTo(cx - w * 0.25f, cy + h * 1.15f)
                canvas.drawPath(iconPath, iconPaint)
            }
            IconKind.SPACE -> {
                iconPaint.color = withAlpha(color, 0x8C)
                iconPaint.strokeWidth = dp(1.7f)
                val w = dp(12f)
                val h = dp(3f)
                iconPath.moveTo(cx - w, cy - h * 0.5f)
                iconPath.lineTo(cx - w, cy + h)
                iconPath.lineTo(cx + w, cy + h)
                iconPath.lineTo(cx + w, cy - h * 0.5f)
                canvas.drawPath(iconPath, iconPaint)
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutKeys()
    }

    private fun buildKeysIfNeeded() {
        if (keys.isEmpty()) {
            keys = buildLayout()
            layoutKeys()
        } else if (modeChanged) {
            keys = buildLayout()
            layoutKeys()
            lastMode = mode
        }
    }

    private var lastMode: Mode? = null
    private val modeChanged: Boolean get() = lastMode != mode

    private fun layoutKeys() {
        lastMode = mode
        t9RailRect = null
        if (keys.isEmpty()) return
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val gapX = dp(4.5f)
        val gapY = dp(6.5f)
        val edgeX = dp(4f)
        val padTop = dp(5f)
        val padBottom = dp(6f)
        val usableH = h - padTop - padBottom

        if (mode == Mode.T9_PINYIN) {
            layoutT9Keys(w, usableH, edgeX, padTop, gapX, gapY)
            return
        }

        val rows = keys.groupBy { it.row }
        val rowCount = rows.keys.maxOrNull()?.plus(1) ?: return
        val rowHeight = (usableH - gapY * (rowCount - 1)) / rowCount

        rows.forEach { (rowIdx, rowKeys) ->
            val totalWeight = rowKeys.sumOf { it.width.toDouble() }.toFloat()
            if (totalWeight <= 0f) return@forEach
            val padUnits = rowKeys.firstOrNull()?.pad ?: 0f
            val count = rowKeys.size
            val avail = w - edgeX * 2 - gapX * (count - 1)
            val unitW = avail / (totalWeight + padUnits * 2f)
            val padPx = unitW * padUnits
            var x = edgeX + padPx
            val y = padTop + rowIdx * (rowHeight + gapY)
            rowKeys.sortedBy { it.col }.forEach { key ->
                val kw = unitW * key.width
                key.rect = RectF(x, y, x + kw, y + rowHeight)
                x += kw + gapX
            }
        }
    }

    private fun layoutT9Keys(
        w: Float, usableH: Float, edgeX: Float, padTop: Float, gapX: Float, gapY: Float
    ) {
        val rowHeight = (usableH - gapY * 3f) / 4f
        val topAreaH = rowHeight * 3f + gapY * 2f
        val totalAvailW = w - edgeX * 2f - gapX * 2f
        val leftW = totalAvailW * 0.16f
        val centerW = totalAvailW * 0.65f
        val rightW = totalAvailW * 0.19f

        val leftX = edgeX
        val centerX = leftX + leftW + gapX
        val rightX = centerX + centerW + gapX

        t9RailRect = RectF(leftX, padTop, leftX + leftW, padTop + topAreaH)
        val railKeys = keys.filter { it.isPunctRail }.sortedBy { it.col }
        val railItemH = topAreaH / railKeys.size.coerceAtLeast(1)
        railKeys.forEachIndexed { idx, k ->
            val y = padTop + idx * railItemH
            k.rect = RectF(leftX, y, leftX + leftW, y + railItemH)
        }

        val cellW = (centerW - gapX * 2f) / 3f
        val centerKeys = keys.filter { it.isT9 && !it.isPunctRail && it.row in 0..2 }
        centerKeys.forEach { k ->
            val cx = centerX + k.col * (cellW + gapX)
            val cy = padTop + k.row * (rowHeight + gapY)
            k.rect = RectF(cx, cy, cx + cellW, cy + rowHeight)
        }

        val rightKeys = keys.filter { !it.isT9 && !it.isPunctRail && it.row in 0..2 }
        rightKeys.forEach { k ->
            val cy = padTop + k.row * (rowHeight + gapY)
            k.rect = RectF(rightX, cy, rightX + rightW, cy + rowHeight)
        }

        val bottomKeys = keys.filter { it.row == 3 }.sortedBy { it.col }
        val totalWeight = bottomKeys.sumOf { it.width.toDouble() }.toFloat()
        val availBottom = w - edgeX * 2f - gapX * (bottomKeys.size - 1)
        val unitW = availBottom / totalWeight
        var bx = edgeX
        val by = padTop + 3f * (rowHeight + gapY)
        bottomKeys.forEach { k ->
            val kw = unitW * k.width
            k.rect = RectF(bx, by, bx + kw, by + rowHeight)
            bx += kw + gapX
        }
    }

    private fun buildLayout(): List<Key> = when (mode) {
        Mode.QWERTY_PINYIN, Mode.QWERTY_EN -> buildQwerty()
        Mode.T9_PINYIN -> buildT9()
        Mode.SYMBOLS -> buildSymbols()
        Mode.NUMBERS -> buildNumbers()
        Mode.EMOJI -> emptyList()
    }

    private fun buildQwerty(): List<Key> {
        val isEn = mode == Mode.QWERTY_EN
        return listOf(
            Key("q","q",-1,row=0,col=0f,hint="1"), Key("w","w",-1,row=0,col=1f,hint="2"),
            Key("e","e",-1,row=0,col=2f,hint="3"), Key("r","r",-1,row=0,col=3f,hint="4"),
            Key("t","t",-1,row=0,col=4f,hint="5"), Key("y","y",-1,row=0,col=5f,hint="6"),
            Key("u","u",-1,row=0,col=6f,hint="7"), Key("i","i",-1,row=0,col=7f,hint="8"),
            Key("o","o",-1,row=0,col=8f,hint="9"), Key("p","p",-1,row=0,col=9f,hint="0"),
            Key("a","a",-1,row=1,col=0f,pad=0.5f), Key("s","s",-1,row=1,col=1f),
            Key("d","d",-1,row=1,col=2f), Key("f","f",-1,row=1,col=3f),
            Key("g","g",-1,row=1,col=4f), Key("h","h",-1,row=1,col=5f),
            Key("j","j",-1,row=1,col=6f), Key("k","k",-1,row=1,col=7f),
            Key("l","l",-1,row=1,col=8f),
            Key("","",-1,special=SpecialKey.SHIFT,row=2,col=0f,width=1.45f),
            Key("z","z",-1,row=2,col=1f), Key("x","x",-1,row=2,col=2f),
            Key("c","c",-1,row=2,col=3f), Key("v","v",-1,row=2,col=4f),
            Key("b","b",-1,row=2,col=5f), Key("n","n",-1,row=2,col=6f),
            Key("m","m",-1,row=2,col=7f),
            Key("","",-1,special=SpecialKey.DELETE,row=2,col=8f,width=1.45f),
            Key("符","",-1,special=SpecialKey.SYMBOL,row=3,col=0f,width=1.05f),
            Key("123","",-1,special=SpecialKey.NUMBER,row=3,col=1f,width=1.15f),
            Key(if (isEn) "," else "，", if (isEn) "," else "，", -1, row=3, col=2f, width=0.85f, isCjkPunct=!isEn),
            Key(""," ",-1,special=SpecialKey.SPACE,row=3,col=3f,width=3.75f),
            Key("","",-1,special=SpecialKey.LANG_SWITCH,row=3,col=4f,width=1.2f,isDualLang=true),
            Key("","",-1,special=SpecialKey.ENTER,row=3,col=5f,width=1.65f,isAccent=true)
        )
    }

    private fun buildT9(): List<Key> = listOf(
        Key("，","，",-1,row=0,col=0f,isCjkPunct=true,isPunctRail=true),
        Key("。","。",-1,row=0,col=1f,isCjkPunct=true,isPunctRail=true),
        Key("？","？",-1,row=0,col=2f,isCjkPunct=true,isPunctRail=true),
        Key("！","！",-1,row=0,col=3f,isCjkPunct=true,isPunctRail=true),
        Key("@/.","@",-1,row=0,col=0f,hint="1",isT9=true),
        Key("ABC","2",-1,row=0,col=1f,hint="2",isT9=true),
        Key("DEF","3",-1,row=0,col=2f,hint="3",isT9=true),
        Key("GHI","4",-1,row=1,col=0f,hint="4",isT9=true),
        Key("JKL","5",-1,row=1,col=1f,hint="5",isT9=true),
        Key("MNO","6",-1,row=1,col=2f,hint="6",isT9=true),
        Key("PQRS","7",-1,row=2,col=0f,hint="7",isT9=true),
        Key("TUV","8",-1,row=2,col=1f,hint="8",isT9=true),
        Key("WXYZ","9",-1,row=2,col=2f,hint="9",isT9=true),
        Key("","",-1,special=SpecialKey.DELETE,row=0,col=3f),
        Key("重输","",-1,special=SpecialKey.CLEAR_COMP,row=1,col=3f),
        Key("0","0",-1,row=2,col=3f),
        Key("符","",-1,special=SpecialKey.SYMBOL,row=3,col=0f,width=0.95f),
        Key("123","",-1,special=SpecialKey.NUMBER,row=3,col=1f,width=1.05f),
        Key(""," ",-1,special=SpecialKey.SPACE,row=3,col=2f,width=2.4f),
        Key("","",-1,special=SpecialKey.LANG_SWITCH,row=3,col=3f,width=1.05f,isDualLang=true),
        Key("","",-1,special=SpecialKey.ENTER,row=3,col=4f,width=1.35f,isAccent=true)
    )

    private fun buildSymbols(): List<Key> {
        val row0 = listOf("1","2","3","4","5","6","7","8","9","0")
        val row1 = listOf("@","#","￥","%","&","*","-","+","=","/")
        val row2 = listOf("!","\"","'",":",";","?","（","）","《","》")
        val list = mutableListOf<Key>()
        row0.forEachIndexed { i, s -> list.add(Key(s, s, -1, row = 0, col = i.toFloat())) }
        row1.forEachIndexed { i, s -> list.add(Key(s, s, -1, row = 1, col = i.toFloat())) }
        row2.forEachIndexed { i, s -> list.add(Key(s, s, -1, row = 2, col = i.toFloat())) }
        list.add(Key("返回", "", -1, special = SpecialKey.MODE_SWITCH, row = 3, col = 0f, width = 1.8f))
        list.add(Key("", " ", -1, special = SpecialKey.SPACE, row = 3, col = 1f, width = 4.6f))
        list.add(Key("", "", -1, special = SpecialKey.DELETE, row = 3, col = 2f, width = 1.7f))
        list.add(Key("", "", -1, special = SpecialKey.ENTER, row = 3, col = 3f, width = 1.9f, isAccent = true))
        return list
    }

    private fun buildNumbers(): List<Key> = listOf(
        Key("1","1",-1,row=0,col=0f), Key("2","2",-1,row=0,col=1f), Key("3","3",-1,row=0,col=2f),
        Key("","",-1,special=SpecialKey.DELETE,row=0,col=3f),
        Key("4","4",-1,row=1,col=0f), Key("5","5",-1,row=1,col=1f), Key("6","6",-1,row=1,col=2f),
        Key("返回","",-1,special=SpecialKey.MODE_SWITCH,row=1,col=3f),
        Key("7","7",-1,row=2,col=0f), Key("8","8",-1,row=2,col=1f), Key("9","9",-1,row=2,col=2f),
        Key(""," ",-1,special=SpecialKey.SPACE,row=2,col=3f),
        Key("符","",-1,special=SpecialKey.SYMBOL,row=3,col=0f),
        Key("0","0",-1,row=3,col=1f), Key(".",".",-1,row=3,col=2f),
        Key("","",-1,special=SpecialKey.ENTER,row=3,col=3f,isAccent=true)
    )

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val key = keyAt(event.x, event.y) ?: return false
                dismissLongPressPopup()
                touchX = event.x
                touchY = event.y
                isTouching = theme.isDark
                glowAlpha = if (theme.isDark) 1f else 0f
                lastFrameMs = SystemClock.uptimeMillis()

                currentPressed = key
                key.pressed = true
                performHaptic()
                invalidate()
                startLongPress(key)
            }
            MotionEvent.ACTION_MOVE -> {
                touchX = event.x
                touchY = event.y
                // 如果已弹出长按多选气泡，左右滑动直接切换选中项
                if (longPressOptions.isNotEmpty() && longPressCellRects.isNotEmpty()) {
                    var bestIdx = longPressSelectedIndex
                    var bestDist = Float.MAX_VALUE
                    longPressCellRects.forEachIndexed { idx, r ->
                        val d = kotlin.math.abs(event.x - r.centerX())
                        if (d < bestDist) {
                            bestDist = d
                            bestIdx = idx
                        }
                    }
                    if (bestIdx != longPressSelectedIndex) {
                        longPressSelectedIndex = bestIdx
                        performHaptic()
                    }
                    invalidate()
                    return true
                }

                val key = keyAt(event.x, event.y)
                if (key !== currentPressed) {
                    currentPressed?.pressed = false
                    stopLongPress()
                    currentPressed = key
                    key?.pressed = true
                    if (key != null) {
                        performHaptic()
                        startLongPress(key)
                    }
                }
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                val key = currentPressed
                isTouching = false
                stopLongPress()
                cancelRepeatDelete()
                key?.pressed = false
                currentPressed = null

                if (longPressOptions.isNotEmpty()) {
                    val chosen = longPressOptions.getOrNull(longPressSelectedIndex)
                    dismissLongPressPopup()
                    invalidate()
                    if (!chosen.isNullOrEmpty()) {
                        onKeyLongPress?.invoke(chosen)
                    }
                    return true
                }

                invalidate()
                if (key != null && keyAt(event.x, event.y) === key) {
                    handleKeyTap(key)
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                isTouching = false
                dismissLongPressPopup()
                currentPressed?.pressed = false
                currentPressed = null
                stopLongPress()
                cancelRepeatDelete()
                invalidate()
            }
        }
        return true
    }

    private fun keyAt(x: Float, y: Float): Key? =
        keys.firstOrNull { it.rect.contains(x, y) }

    private fun handleKeyTap(key: Key) {
        if (key.special != null) {
            onSpecialKey?.invoke(key.special)
        } else if (key.output.isNotEmpty()) {
            onKeyPress?.invoke(key.output)
        }
    }

    private fun startLongPress(key: Key) {
        longPressHandler = Runnable {
            if (key.special == SpecialKey.DELETE) {
                key.pressed = false
                invalidate()
                startRepeatDelete()
                return@Runnable
            }
            val options = buildLongPressOptions(key)
            if (options.isNotEmpty()) {
                performHaptic()
                openLongPressPopup(key, options)
                invalidate()
            }
        }
        postDelayed(longPressHandler!!, 360)
    }

    private fun stopLongPress() {
        longPressHandler?.let { removeCallbacks(it) }
        longPressHandler = null
    }

    private fun startRepeatDelete() {
        performHaptic()
        onSpecialKey?.invoke(SpecialKey.DELETE)
        repeatDelete = object : Runnable {
            override fun run() {
                onSpecialKey?.invoke(SpecialKey.DELETE)
                postDelayed(this, 60)
            }
        }
        postDelayed(repeatDelete!!, 200)
    }

    private fun cancelRepeatDelete() {
        repeatDelete?.let { removeCallbacks(it) }
        repeatDelete = null
    }

    private fun performHaptic() {
        if (settings.cachedVibrate) {
            VibratorHelper.vibrate(context, 14)
        }
        if (settings.cachedSound) {
            AudioManagerHelper.playKeyClick(context)
        }
    }

    fun release() {
        stopLongPress()
        cancelRepeatDelete()
        dismissLongPressPopup()
    }
}
