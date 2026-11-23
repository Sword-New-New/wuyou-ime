package com.wuyouai.ime.keyboard

import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.wuyouai.ime.prediction.Candidate
import com.wuyouai.ime.prediction.CandidateEngine
import com.wuyouai.ime.settings.SettingsManager
import com.wuyouai.ime.themes.ThemeManager
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * 无忧输入法核心服务。
 * 真正注册为 Android InputMethodService，在系统设置中可启用并设为默认。
 * 仅在内存中处理当前输入内容，不上传、不记录密码/敏感框内容。
 */
class WuyouInputMethodService : InputMethodService() {

    private lateinit var settings: SettingsManager
    private var candidateEngine: CandidateEngine? = null
    private lateinit var rootView: FrameLayout
    private lateinit var containerView: LinearLayout
    private lateinit var candidateBar: CandidateBarView
    private lateinit var keyboardView: KeyboardView

    private val queryExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "wuyou-candidate").apply { priority = Thread.NORM_PRIORITY - 1 }
    }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val querySeq = AtomicInteger(0)

    private var composing = StringBuilder()
    private var candidates: List<Candidate> = emptyList()
    private var prevWord: String? = null
    private var isPasswordField = false
    private var currentMode = KeyboardView.Mode.QWERTY_PINYIN

    override fun onCreate() {
        super.onCreate()
        settings = SettingsManager.get(this)
        candidateEngine = tryCreateEngine()
    }

    private fun tryCreateEngine(): CandidateEngine? = try {
        CandidateEngine(this)
    } catch (t: Throwable) {
        android.util.Log.w("WuyouIME", "CandidateEngine 初始化失败，降级为空词库模式", t)
        null
    }

    override fun onCreateInputView(): View {
        val theme = ThemeManager.byId(settings.cachedTheme)
        rootView = FrameLayout(this)
        containerView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(theme.bg)
        }

        candidateBar = CandidateBarView(this).apply {
            setTheme(theme)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(44)
            )
        }
        candidateBar.onCandidateSelected = { onCandidatePicked(it) }
        candidateBar.onExpandMore = { showMoreCandidates() }
        candidateBar.onToggleLayout = { toggleQwertyAndT9() }
        candidateBar.onCycleTheme = { cycleNextTheme() }
        candidateBar.onHideKeyboard = { requestHideSelf(0) }

        keyboardView = KeyboardView(this).apply {
            setTheme(theme)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(settings.cachedKbHeight)
            )
        }
        keyboardView.onKeyPress = { onKey(it) }
        keyboardView.onSpecialKey = { onSpecial(it) }
        keyboardView.onKeyLongPress = { onLongPress(it) }

        containerView.addView(candidateBar)
        containerView.addView(keyboardView)
        rootView.addView(containerView)
        updateModeBadge()
        return rootView
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        detectFieldType(attribute)
        resetComposing()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        applyCurrentTheme()
        applyCurrentMode()
        refreshCandidates()
    }

    private fun applyCurrentTheme() {
        val theme = ThemeManager.byId(settings.cachedTheme)
        if (::containerView.isInitialized) containerView.setBackgroundColor(theme.bg)
        if (::rootView.isInitialized) rootView.setBackgroundColor(theme.bg)
        if (::candidateBar.isInitialized) candidateBar.setTheme(theme)
        if (::keyboardView.isInitialized) keyboardView.setTheme(theme)
    }

    private fun cycleNextTheme() {
        val next = ThemeManager.nextAfter(settings.cachedTheme)
        settings.cachedTheme = next.id
        settings.set(SettingsManager.KEY_THEME, next.id)
        applyCurrentTheme()
    }

    private fun toggleQwertyAndT9() {
        val nextLayout = if (currentMode == KeyboardView.Mode.T9_PINYIN) "qwerty" else "t9"
        settings.cachedLayout = nextLayout
        settings.set(SettingsManager.KEY_LAYOUT, nextLayout)
        resetComposing()
        currentInputConnection?.finishComposingText()
        applyCurrentMode()
        refreshCandidates()
    }

    private fun updateModeBadge() {
        if (!::candidateBar.isInitialized) return
        candidateBar.modeLabel = when (currentMode) {
            KeyboardView.Mode.T9_PINYIN -> "九宫格"
            KeyboardView.Mode.QWERTY_EN -> "EN"
            KeyboardView.Mode.SYMBOLS -> "符号"
            KeyboardView.Mode.NUMBERS -> "数字"
            else -> "26键"
        }
    }

    private fun detectFieldType(info: EditorInfo?) {
        val type = info?.inputType ?: 0
        val variation = type and InputType.TYPE_MASK_VARIATION
        isPasswordField = variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                || variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
                || (info?.imeOptions?.let {
                    it and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0
                } ?: false)
    }

    private fun applyCurrentMode() {
        currentMode = when (settings.cachedLayout) {
            "t9" -> KeyboardView.Mode.T9_PINYIN
            else -> KeyboardView.Mode.QWERTY_PINYIN
        }
        if (::keyboardView.isInitialized) {
            keyboardView.mode = currentMode
            keyboardView.keys = emptyList()
            keyboardView.invalidate()
        }
        updateModeBadge()
    }

    private fun resetComposing() {
        composing.clear()
        candidates = emptyList()
        prevWord = null
    }

    private fun onKey(c: String) {
        val ic = currentInputConnection ?: return
        when (currentMode) {
            KeyboardView.Mode.QWERTY_PINYIN, KeyboardView.Mode.T9_PINYIN -> {
                val isPinyinChar = c.length == 1 && (c[0] in 'a'..'z' || (currentMode == KeyboardView.Mode.T9_PINYIN && c[0] in '2'..'9'))
                if (isPinyinChar) {
                    composing.append(c)
                    refreshCandidates()
                    setComposingTextInEditor(ic)
                } else {
                    if (composing.isNotEmpty()) {
                        val first = candidates.firstOrNull()?.text ?: composing.toString()
                        ic.commitText(first + c, 1)
                        resetComposing()
                        refreshCandidates()
                    } else {
                        ic.commitText(c, 1)
                    }
                }
            }
            KeyboardView.Mode.QWERTY_EN -> {
                val out = if (keyboardView.isShift && c.length == 1) c.uppercase() else c
                ic.commitText(out, 1)
                if (keyboardView.isShift && !keyboardView.isCapsLock) {
                    keyboardView.isShift = false
                    keyboardView.invalidate()
                }
            }
            else -> {
                ic.commitText(c, 1)
            }
        }
    }

    private fun setComposingTextInEditor(ic: InputConnection) {
        if (composing.isNotEmpty()) {
            ic.setComposingText(composing.toString(), 1)
        }
    }

    private fun onSpecial(key: KeyboardView.SpecialKey) {
        val ic = currentInputConnection ?: return
        when (key) {
            KeyboardView.SpecialKey.DELETE -> {
                if (composing.isNotEmpty()) {
                    composing.deleteCharAt(composing.length - 1)
                    refreshCandidates()
                    if (composing.isEmpty()) ic.finishComposingText()
                    else ic.setComposingText(composing.toString(), 1)
                } else {
                    ic.deleteSurroundingText(1, 0)
                }
            }
            KeyboardView.SpecialKey.CLEAR_COMP -> {
                if (composing.isNotEmpty()) {
                    resetComposing()
                    ic.finishComposingText()
                    refreshCandidates()
                }
            }
            KeyboardView.SpecialKey.SPACE -> {
                if (composing.isNotEmpty() && candidates.isNotEmpty()) {
                    onCandidatePicked(candidates.first())
                } else {
                    ic.commitText(" ", 1)
                }
            }
            KeyboardView.SpecialKey.ENTER -> {
                if (composing.isNotEmpty()) {
                    ic.commitText(composing.toString(), 1)
                    resetComposing()
                    refreshCandidates()
                } else {
                    ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                    ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
                }
            }
            KeyboardView.SpecialKey.LANG_SWITCH -> {
                currentMode = when (currentMode) {
                    KeyboardView.Mode.QWERTY_PINYIN, KeyboardView.Mode.T9_PINYIN ->
                        KeyboardView.Mode.QWERTY_EN
                    else -> if (settings.cachedLayout == "t9") KeyboardView.Mode.T9_PINYIN else KeyboardView.Mode.QWERTY_PINYIN
                }
                resetComposing()
                ic.finishComposingText()
                keyboardView.mode = currentMode
                keyboardView.keys = emptyList()
                keyboardView.invalidate()
                updateModeBadge()
                refreshCandidates()
            }
            KeyboardView.SpecialKey.SYMBOL -> {
                currentMode = if (currentMode == KeyboardView.Mode.SYMBOLS)
                    (if (settings.cachedLayout == "t9") KeyboardView.Mode.T9_PINYIN else KeyboardView.Mode.QWERTY_PINYIN)
                else KeyboardView.Mode.SYMBOLS
                keyboardView.mode = currentMode
                keyboardView.keys = emptyList()
                keyboardView.invalidate()
                updateModeBadge()
            }
            KeyboardView.SpecialKey.NUMBER -> {
                currentMode = if (currentMode == KeyboardView.Mode.NUMBERS)
                    (if (settings.cachedLayout == "t9") KeyboardView.Mode.T9_PINYIN else KeyboardView.Mode.QWERTY_PINYIN)
                else KeyboardView.Mode.NUMBERS
                keyboardView.mode = currentMode
                keyboardView.keys = emptyList()
                keyboardView.invalidate()
                updateModeBadge()
            }
            KeyboardView.SpecialKey.MODE_SWITCH -> {
                currentMode = if (settings.cachedLayout == "t9") KeyboardView.Mode.T9_PINYIN else KeyboardView.Mode.QWERTY_PINYIN
                keyboardView.mode = currentMode
                keyboardView.keys = emptyList()
                keyboardView.invalidate()
                updateModeBadge()
            }
            KeyboardView.SpecialKey.SHIFT -> {
                keyboardView.isShift = !keyboardView.isShift
                keyboardView.invalidate()
            }
            else -> { }
        }
    }

    /** 长按弹窗选字（支持中英文模式下直接输入 1/Q/q 或 A/a） */
    private fun onLongPress(output: String) {
        val ic = currentInputConnection ?: return
        if (output.isEmpty()) return
        if (composing.isNotEmpty()) {
            val first = candidates.firstOrNull()?.text ?: composing.toString()
            ic.commitText(first + output, 1)
            resetComposing()
            refreshCandidates()
        } else {
            ic.commitText(output, 1)
        }
    }

    private fun refreshCandidates() {
        if (composing.isEmpty()) {
            candidates = emptyList()
            candidateBar.setCandidates(emptyList())
            return
        }
        val engine = candidateEngine ?: run {
            candidates = emptyList()
            candidateBar.setCandidates(emptyList())
            return
        }
        val seq = querySeq.incrementAndGet()
        val text = composing.toString()
        val pv = prevWord
        val fz = settings.cachedFuzzy
        val cc = settings.cachedCorrect
        val lim = settings.cachedCandCount
        val eng = engine
        queryExecutor.execute {
            val result = try {
                eng.query(input = text, prevWord = pv, fuzzy = fz, correct = cc, limit = lim)
            } catch (t: Throwable) {
                android.util.Log.w("WuyouIME", "候选查询失败", t)
                emptyList()
            }
            mainHandler.post {
                if (seq != querySeq.get()) return@post
                if (composing.toString() != text) return@post
                candidates = result
                candidateBar.setCandidates(result)
            }
        }
    }

    private fun showMoreCandidates() {
        val engine = candidateEngine ?: return
        candidates = engine.query(
            composing.toString(), prevWord,
            settings.cachedFuzzy, settings.cachedCorrect, limit = 60
        )
        candidateBar.setCandidates(candidates)
    }

    private fun onCandidatePicked(cand: Candidate) {
        val ic = currentInputConnection ?: return
        ic.commitText(cand.text, 1)

        val consumed = if (cand.consumedLetters > 0) {
            cand.consumedLetters.coerceAtMost(composing.length)
        } else {
            composing.length
        }
        val remain = if (consumed >= composing.length) {
            ""
        } else {
            composing.substring(consumed)
        }

        composing.setLength(0)
        composing.append(remain)

        if (!isPasswordField && !settings.cachedPrivacyMode && settings.cachedPersonalize) {
            try {
                com.wuyouai.ime.personalization.UserModel.get(this)
                    .learn(cand.text, cand.pinyin, prevWord)
            } catch (t: Throwable) {
                android.util.Log.w("WuyouIME", "学习记录失败，已忽略", t)
            }
        }
        prevWord = cand.text

        if (composing.isEmpty()) {
            ic.finishComposingText()
            candidates = emptyList()
            candidateBar.setCandidates(emptyList())
        } else {
            ic.setComposingText(composing.toString(), 1)
            refreshCandidates()
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        resetComposing()
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
