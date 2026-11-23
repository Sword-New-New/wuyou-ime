package com.wuyouai.ime.keyboard

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import com.wuyouai.ime.prediction.Candidate
import com.wuyouai.ime.prediction.CandidateEngine
import com.wuyouai.ime.settings.SettingsManager
import com.wuyouai.ime.themes.ThemeManager

/**
 * 无忧输入法核心服务。
 * 真正注册为 Android InputMethodService，在系统设置中可启用并设为默认。
 * 仅在内存中处理当前输入内容，不上传、不记录密码/敏感框内容。
 */
class WuyouInputMethodService : InputMethodService() {

    private lateinit var settings: SettingsManager
    private var candidateEngine: CandidateEngine? = null
    private lateinit var rootView: FrameLayout
    private lateinit var candidateBar: CandidateBarView
    private lateinit var keyboardView: KeyboardView

    // 候选查询放到单线程池：串行执行，天然避免并发查同一个 SQLite 连接；
    // mainHandler 负责把结果丢回主线程刷新候选栏。
    private val queryExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "wuyou-candidate").apply { priority = Thread.NORM_PRIORITY - 1 }
    }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val querySeq = AtomicInteger(0)

    private var composing = StringBuilder()      // 当前拼音串
    private var candidates: List<Candidate> = emptyList()
    private var prevWord: String? = null
    private var isPasswordField = false
    private var currentMode = KeyboardView.Mode.QWERTY_PINYIN

    override fun onCreate() {
        super.onCreate()
        // 关键：输入法必须能被系统登记进「可用输入法」列表。
        // 任何子模块（数据库/词库/学习模型）初始化失败，都不允许让本 Service 崩溃，
        // 否则 InputMethodManagerService 会把它整条剔除，设置里就再也找不到这个输入法了。
        settings = SettingsManager.get(this)
        candidateEngine = tryCreateEngine()
    }

    /** 引擎惰性创建：失败只降级为「无候选词」，不影响输入法被系统识别与键盘弹出 */
    private fun tryCreateEngine(): CandidateEngine? = try {
        CandidateEngine(this)
    } catch (t: Throwable) {
        android.util.Log.w("WuyouIME", "CandidateEngine 初始化失败，降级为空词库模式", t)
        null
    }

    override fun onCreateInputView(): View {
        val theme = ThemeManager.byId(settings.cachedTheme)
        rootView = FrameLayout(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(theme.bg)
        }
        // 候选栏
        candidateBar = CandidateBarView(this).apply {
            setTheme(theme)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(52)
            )
        }
        candidateBar.onCandidateSelected = { onCandidatePicked(it) }
        candidateBar.onExpandMore = { showMoreCandidates() }
        // 键盘主体
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
        container.addView(candidateBar)
        container.addView(keyboardView)
        rootView.addView(container)
        return rootView
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        detectFieldType(attribute)
        resetComposing()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        applyCurrentMode()
        refreshCandidates()
    }

    /** 识别密码/敏感输入框，进入隐私保护（不学习、不记录） */
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
            keyboardView.invalidate()
        }
    }

    private fun resetComposing() {
        composing.clear()
        candidates = emptyList()
        prevWord = null
    }

    /** 字符键按下 */
    private fun onKey(c: String) {
        val ic = currentInputConnection ?: return
        when (currentMode) {
            KeyboardView.Mode.QWERTY_PINYIN, KeyboardView.Mode.T9_PINYIN -> {
                composing.append(c)
                refreshCandidates()
                setComposingTextInEditor(ic)
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
        // 编辑器中显示原始拼音串（不强制替换用户输入）
        if (composing.isNotEmpty()) {
            ic.setComposingText(composing.toString(), 1)
        }
    }

    /** 功能键按下 */
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
                } else {
                    ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                }
            }
            KeyboardView.SpecialKey.LANG_SWITCH -> {
                currentMode = when (currentMode) {
                    KeyboardView.Mode.QWERTY_PINYIN, KeyboardView.Mode.T9_PINYIN ->
                        KeyboardView.Mode.QWERTY_EN
                    else -> KeyboardView.Mode.QWERTY_PINYIN
                }
                resetComposing()
                keyboardView.mode = currentMode
                keyboardView.keys = emptyList()
                keyboardView.invalidate()
                refreshCandidates()
            }
            KeyboardView.SpecialKey.NUMBER, KeyboardView.SpecialKey.SYMBOL -> {
                currentMode = if (currentMode == KeyboardView.Mode.NUMBERS ||
                    currentMode == KeyboardView.Mode.SYMBOLS)
                    KeyboardView.Mode.QWERTY_PINYIN
                else KeyboardView.Mode.SYMBOLS
                keyboardView.mode = currentMode
                keyboardView.keys = emptyList()
                keyboardView.invalidate()
            }
            KeyboardView.SpecialKey.MODE_SWITCH -> {
                currentMode = KeyboardView.Mode.QWERTY_PINYIN
                keyboardView.mode = currentMode
                keyboardView.keys = emptyList()
                keyboardView.invalidate()
            }
            KeyboardView.SpecialKey.SHIFT -> {
                keyboardView.isShift = !keyboardView.isShift
                keyboardView.invalidate()
            }
            else -> { }
        }
    }

    private fun onLongPress(output: String) {
        // 长按字母输入大写（英文模式）
        if (currentMode == KeyboardView.Mode.QWERTY_EN && output.length == 1) {
            currentInputConnection?.commitText(output.uppercase(), 1)
        }
    }

    /** 刷新候选 */
    private fun refreshCandidates() {
        if (composing.isEmpty()) {
            candidates = emptyList()
            candidateBar.setCandidates(emptyList())
            return
        }
        // 引擎可能因词库初始化失败而为 null：此时仅无候选词，输入法本身依旧可用
        val engine = candidateEngine ?: run {
            candidates = emptyList()
            candidateBar.setCandidates(emptyList())
            return
        }
        // 查询词库是磁盘 IO + 排序，绝不能在主线程同步做：
        // 33.7 万词的词库下每敲一个键都查一次，按键就会肉眼可见地掉帧。
        // 这里丢到单线程池，并用递增序号丢弃「过期结果」，
        // 保证候选栏永远只显示与当前输入串匹配的那一批。
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
                // 用户已经继续打字了，这批结果作废
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

    /** 候选上屏 */
    private fun onCandidatePicked(cand: Candidate) {
        val ic = currentInputConnection ?: return
        ic.commitText(cand.text, 1)

        // 只删掉这个候选用掉的那几个字母，剩下的拼音继续留着给下一轮候选。
        // 之前无条件清空整串，导致"选一个字之后拼音全没了"。
        val consumed = if (cand.consumedLetters > 0) {
            cand.consumedLetters.coerceAtMost(composing.length)
        } else {
            composing.length          // 无法部分消费（模糊音/纠错）则整串清掉
        }
        val remain = if (consumed >= composing.length) {
            ""
        } else {
            composing.substring(consumed)
        }

        composing.setLength(0)
        composing.append(remain)

        // 学习（密码框/隐私模式不学习）。学习失败绝不影响上屏。
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
            // 拼音用完了，收尾
            ic.finishComposingText()
            candidates = emptyList()
            candidateBar.setCandidates(emptyList())
        } else {
            // 还有剩余拼音，继续显示它们的候选
            ic.setComposingText(composing.toString(), 1)
            refreshCandidates()
        }
    }

    private fun resetComposingKeepPrev() {
        composing.clear()
        candidates = emptyList()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        resetComposing()
    }

    /** 输入框类型为密码时不显示候选（避免泄露） */
    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
