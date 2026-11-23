package com.wuyouai.ime.settings

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.dataStore by preferencesDataStore(name = "wuyou_settings")

/**
 * 设置中心：基于 DataStore 的本地配置。
 * 所有偏好本地存储，不上传。
 */
class SettingsManager private constructor(private val context: Context) {

    companion object {
        @Volatile private var instance: SettingsManager? = null
        fun get(context: Context): SettingsManager =
            instance ?: synchronized(this) {
                instance ?: SettingsManager(context.applicationContext).also { instance = it }
            }

        // 输入设置
        val KEY_LAYOUT = stringPreferencesKey("layout")           // qwerty / t9
        val KEY_KB_HEIGHT = intPreferencesKey("kb_height")        // 键盘高度 dp
        val KEY_SOUND = booleanPreferencesKey("sound")
        val KEY_VIBRATE = booleanPreferencesKey("vibrate")
        val KEY_LONGPRESS_MS = intPreferencesKey("longpress_ms")

        // 中文输入
        val KEY_FULL_PINYIN = booleanPreferencesKey("full_pinyin")
        val KEY_SIMPLIFIED = booleanPreferencesKey("simplified")  // 简拼
        val KEY_FUZZY = booleanPreferencesKey("fuzzy")            // 模糊音
        val KEY_CORRECT = booleanPreferencesKey("correct")        // 智能纠错
        val KEY_CAND_COUNT = intPreferencesKey("cand_count")
        val KEY_ASSOCIATE = booleanPreferencesKey("associate")    // 词语联想
        val KEY_LONG_SENTENCE = booleanPreferencesKey("long_sentence")

        // 英文输入
        val KEY_EN_AUTOCAP = booleanPreferencesKey("en_autocap")
        val KEY_EN_AUTOSPACE = booleanPreferencesKey("en_autospace")
        val KEY_EN_CORRECT = booleanPreferencesKey("en_correct")
        val KEY_EN_PREDICT = booleanPreferencesKey("en_predict")

        // 智能输入
        val KEY_CONTEXT_PREDICT = booleanPreferencesKey("context_predict")
        val KEY_PERSONALIZE = booleanPreferencesKey("personalize")
        val KEY_SMART_PHRASE = booleanPreferencesKey("smart_phrase")
        val KEY_SYNONYM = booleanPreferencesKey("synonym")

        // 隐私
        val KEY_PRIVACY_MODE = booleanPreferencesKey("privacy_mode") // 隐私模式：不学习
        val KEY_CLIPBOARD_HISTORY = booleanPreferencesKey("clipboard_history")

        // 外观
        val KEY_THEME = stringPreferencesKey("theme")             // 主题 id
        val KEY_FONT_SIZE = intPreferencesKey("font_size")
        val KEY_KEY_RADIUS = intPreferencesKey("key_radius")
        // 引导页协议确认（仅记录"已同意"，不记录任何输入内容）
        val KEY_AGREED = booleanPreferencesKey("agreed")
        val KEY_AGREED_VERSION = stringPreferencesKey("agreed_version")
        val KEY_AGREED_AT = longPreferencesKey("agreed_at")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // —— 输入设置 ——
    val layout: Flow<String> = context.dataStore.data.map { it[KEY_LAYOUT] ?: "qwerty" }
    val kbHeight: Flow<Int> = context.dataStore.data.map { it[KEY_KB_HEIGHT] ?: 240 }
    val sound: Flow<Boolean> = context.dataStore.data.map { it[KEY_SOUND] ?: false }
    val vibrate: Flow<Boolean> = context.dataStore.data.map { it[KEY_VIBRATE] ?: true }
    val longpressMs: Flow<Int> = context.dataStore.data.map { it[KEY_LONGPRESS_MS] ?: 400 }

    // —— 中文 ——
    val fullPinyin: Flow<Boolean> = context.dataStore.data.map { it[KEY_FULL_PINYIN] ?: true }
    val simplified: Flow<Boolean> = context.dataStore.data.map { it[KEY_SIMPLIFIED] ?: true }
    val fuzzy: Flow<Boolean> = context.dataStore.data.map { it[KEY_FUZZY] ?: false }
    val correct: Flow<Boolean> = context.dataStore.data.map { it[KEY_CORRECT] ?: true }
    val candCount: Flow<Int> = context.dataStore.data.map { it[KEY_CAND_COUNT] ?: 20 }
    val associate: Flow<Boolean> = context.dataStore.data.map { it[KEY_ASSOCIATE] ?: true }
    val longSentence: Flow<Boolean> = context.dataStore.data.map { it[KEY_LONG_SENTENCE] ?: true }

    // —— 英文 ——
    val enAutocap: Flow<Boolean> = context.dataStore.data.map { it[KEY_EN_AUTOCAP] ?: true }
    val enAutospace: Flow<Boolean> = context.dataStore.data.map { it[KEY_EN_AUTOSPACE] ?: true }
    val enCorrect: Flow<Boolean> = context.dataStore.data.map { it[KEY_EN_CORRECT] ?: true }
    val enPredict: Flow<Boolean> = context.dataStore.data.map { it[KEY_EN_PREDICT] ?: true }

    // —— 智能 ——
    val contextPredict: Flow<Boolean> = context.dataStore.data.map { it[KEY_CONTEXT_PREDICT] ?: true }
    val personalize: Flow<Boolean> = context.dataStore.data.map { it[KEY_PERSONALIZE] ?: true }
    val smartPhrase: Flow<Boolean> = context.dataStore.data.map { it[KEY_SMART_PHRASE] ?: true }
    val synonym: Flow<Boolean> = context.dataStore.data.map { it[KEY_SYNONYM] ?: true }

    // —— 隐私 ——
    val privacyMode: Flow<Boolean> = context.dataStore.data.map { it[KEY_PRIVACY_MODE] ?: false }
    val clipboardHistory: Flow<Boolean> = context.dataStore.data.map { it[KEY_CLIPBOARD_HISTORY] ?: false }

    // —— 外观 ——
    val theme: Flow<String> = context.dataStore.data.map { it[KEY_THEME] ?: "pure_white" }
    val fontSize: Flow<Int> = context.dataStore.data.map { it[KEY_FONT_SIZE] ?: 18 }
    val keyRadius: Flow<Int> = context.dataStore.data.map { it[KEY_KEY_RADIUS] ?: 8 }

    /** 是否已完成引导页协议确认 */
    val agreed: Flow<Boolean> = context.dataStore.data.map { it[KEY_AGREED] ?: false }

    // 同步读取缓存（服务内高频使用）
    @Volatile var cachedFuzzy = false
    @Volatile var cachedCorrect = true
    @Volatile var cachedSimplified = true
    @Volatile var cachedLayout = "qwerty"
    @Volatile var cachedTheme = "pure_white"
    @Volatile var cachedKbHeight = 240
    @Volatile var cachedPrivacyMode = false
    @Volatile var cachedPersonalize = true
    @Volatile var cachedVibrate = true
    @Volatile var cachedSound = false
    @Volatile var cachedCandCount = 20
    @Volatile var cachedAssociate = true
    @Volatile var cachedClipboardHistory = false
    @Volatile var cachedKeyRadius = 8
    @Volatile var cachedAgreed = false

    fun <T> set(key: Preferences.Key<T>, value: T) {
        scope.launch { context.dataStore.edit { it[key] = value } }
    }

    /** 启动时订阅，维护同步缓存 */
    fun startCaching() {
        scope.launch {
            fuzzy.collect { cachedFuzzy = it }
        }
        scope.launch { correct.collect { cachedCorrect = it } }
        scope.launch { simplified.collect { cachedSimplified = it } }
        scope.launch { layout.collect { cachedLayout = it } }
        scope.launch { theme.collect { cachedTheme = it } }
        scope.launch { kbHeight.collect { cachedKbHeight = it } }
        scope.launch { privacyMode.collect { cachedPrivacyMode = it } }
        scope.launch { personalize.collect { cachedPersonalize = it } }
        scope.launch { vibrate.collect { cachedVibrate = it } }
        scope.launch { sound.collect { cachedSound = it } }
        scope.launch { candCount.collect { cachedCandCount = it } }
        scope.launch { associate.collect { cachedAssociate = it } }
        scope.launch { clipboardHistory.collect { cachedClipboardHistory = it } }
        scope.launch { keyRadius.collect { cachedKeyRadius = it } }
        scope.launch { agreed.collect { cachedAgreed = it } }
    }
}
