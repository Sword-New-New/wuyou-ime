package com.wuyouai.ime.personalization

import android.content.Context
import android.content.SharedPreferences
import com.wuyouai.ime.prediction.Candidate
import com.wuyouai.ime.storage.DictionaryDatabase
import java.util.concurrent.ConcurrentHashMap

/**
 * 本地个性化学习模型。
 * 记录用户选词频率、上下文搭配、常用短语，全部保存在本地私有数据库。
 * 尊重隐私开关：密码框/敏感框/隐私模式下不学习。
 */
class UserModel private constructor(context: Context) {

    companion object {
        @Volatile private var instance: UserModel? = null
        fun get(context: Context): UserModel =
            instance ?: synchronized(this) {
                instance ?: UserModel(context.applicationContext).also { instance = it }
            }
        private const val PREF = "user_model"
        private const val KEY_LEARN = "learning_enabled"
        private const val KEY_CONTEXT = "context_enabled"
    }

    private val db = DictionaryDatabase.get(context)
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    // 内存缓存最近上下文，避免频繁查库
    private val recentContext = ConcurrentHashMap<String, Int>()
    private var lastCommitted: String? = null

    fun isLearningEnabled(): Boolean = prefs.getBoolean(KEY_LEARN, true)
    fun setLearningEnabled(v: Boolean) = prefs.edit().putBoolean(KEY_LEARN, v).apply()
    fun isContextEnabled(): Boolean = prefs.getBoolean(KEY_CONTEXT, true)
    fun setContextEnabled(v: Boolean) = prefs.edit().putBoolean(KEY_CONTEXT, v).apply()

    /** 记录用户选择了一个候选词（学习） */
    fun learn(word: String, pinyin: String, prevWord: String?) {
        if (!isLearningEnabled()) return
        if (word.isBlank() || word.length > 12) return
        val now = System.currentTimeMillis()
        val wdb = db.writableDatabase
        // 更新用户词表
        wdb.execSQL(
            """INSERT INTO ${DictionaryDatabase.T_USER_WORD}(word,pinyin,count,last_used)
               VALUES(?,?,1,?)
               ON CONFLICT(word) DO UPDATE SET count=count+1, last_used=?""",
            arrayOf(word, pinyin, now, now)
        )
        // 更新上下文搭配
        if (isContextEnabled() && !prevWord.isNullOrBlank()) {
            val key = "$prevWord\u0001$word"
            recentContext.merge(key, 1, Int::plus)
            wdb.execSQL(
                """INSERT INTO user_context(pair,count) VALUES(?,1)
                   ON CONFLICT(pair) DO UPDATE SET count=count+1""",
                arrayOf(key)
            ).let { }
        }
        lastCommitted = word
    }

    /** 用户词得分加成 */
    fun userScore(word: String): Double {
        // 简单实现：用户词表在 query 阶段单独注入，这里对基础词给予用户历史加成
        return 0.0
    }

    /** 上下文得分 */
    fun contextScore(prevWord: String, candidate: String): Double {
        if (!isContextEnabled()) return 0.0
        val key = "$prevWord\u0001$candidate"
        recentContext[key]?.let { return it * 50.0 }
        return 0.0
    }

    /** 将用户高频词注入候选 */
    fun boostUserWords(input: String, prevWord: String?, merged: LinkedHashMap<String, Candidate>) {
        if (input.length < 1) return
        db.readableDatabase.rawQuery(
            """SELECT word,pinyin,count FROM ${DictionaryDatabase.T_USER_WORD}
               WHERE pinyin LIKE ? OR word LIKE ? ORDER BY count DESC LIMIT 5""",
            arrayOf("$input%", "$input%")
        ).use { c ->
            while (c.moveToNext()) {
                val text = c.getString(0)
                val py = c.getString(1)
                val count = c.getInt(2)
                val score = count * 300.0
                val existing = merged[text]
                if (existing != null) {
                    merged[text] = existing.copy(score = existing.score + score, source = 2)
                } else {
                    merged[text] = Candidate(text, py, score, 2)
                }
            }
        }
    }

    /** 联想：基于最近输入给出下一个可能词 */
    fun associate(lastWord: String): List<String> {
        if (!isContextEnabled()) return emptyList()
        val out = mutableListOf<String>()
        db.readableDatabase.rawQuery(
            """SELECT word FROM ${DictionaryDatabase.T_USER_WORD}
               ORDER BY last_used DESC LIMIT 30""",
            null
        ).use { c ->
            while (c.moveToNext() && out.size < 5) {
                val w = c.getString(0)
                if (w != lastWord) out.add(w)
            }
        }
        return out
    }

    /** 同义词/相关词（本地词库：同拼音不同字 + 高频相关词） */
    fun synonymsFor(word: String): List<String> {
        val out = mutableListOf<String>()
        // 取同拼音的其他词
        db.readableDatabase.rawQuery(
            "SELECT pinyin FROM ${DictionaryDatabase.T_WORD} WHERE word=? LIMIT 1",
            arrayOf(word)
        ).use { c ->
            if (c.moveToFirst()) {
                val py = c.getString(0)
                db.readableDatabase.rawQuery(
                    "SELECT word FROM ${DictionaryDatabase.T_WORD} WHERE pinyin=? AND word<>? ORDER BY freq DESC LIMIT 8",
                    arrayOf(py, word)
                ).use { c2 ->
                    while (c2.moveToNext()) out.add(c2.getString(0))
                }
            }
        }
        return out
    }

    /** 清除全部学习数据 */
    fun clearLearningData() {
        db.wipeUserTables()
        recentContext.clear()
        lastCommitted = null
    }

    /** 导出用户词库（备份用，不含敏感输入） */
    fun exportUserWords(): String {
        val sb = StringBuilder()
        db.readableDatabase.rawQuery(
            "SELECT word,pinyin,count FROM ${DictionaryDatabase.T_USER_WORD} ORDER BY count DESC",
            null
        ).use { c ->
            while (c.moveToNext()) {
                sb.append(c.getString(0)).append('\t')
                    .append(c.getString(1)).append('\t')
                    .append(c.getInt(2)).append('\n')
            }
        }
        return sb.toString()
    }

    /** 批量导入用户词 */
    fun importUserWords(text: String) {
        val wdb = db.writableDatabase
        wdb.beginTransaction()
        try {
            text.lineSequence().forEach { line ->
                val parts = line.split('\t')
                if (parts.isNotEmpty() && parts[0].isNotBlank()) {
                    val word = parts[0]
                    val py = parts.getOrNull(1) ?: ""
                    val cnt = parts.getOrNull(2)?.toIntOrNull() ?: 1
                    wdb.execSQL(
                        """INSERT INTO ${DictionaryDatabase.T_USER_WORD}(word,pinyin,count,last_used)
                           VALUES(?,?,?,?) ON CONFLICT(word) DO UPDATE SET count=count+?""",
                        arrayOf(word, py, cnt, System.currentTimeMillis(), cnt)
                    )
                }
            }
            wdb.setTransactionSuccessful()
        } finally {
            wdb.endTransaction()
        }
    }
}
