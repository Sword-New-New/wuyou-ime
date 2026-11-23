package com.wuyouai.ime.personalization

import com.wuyouai.ime.storage.DictionaryDatabase

/**
 * 智能短语：内置常用短语 + 用户自定义缩写展开。
 * 例如自定义 dz -> 指定短语。
 */
object SmartPhrase {

    data class Phrase(val abbrev: String, val content: String, val category: String)

    /** 内置常用短语库 */
    val builtin: List<Phrase> = listOf(
        Phrase("nh", "你好", "问候"),
        Phrase("zsj", "早上好", "问候"),
        Phrase("wsa", "晚上好", "问候"),
        Phrase("xw", "谢谢", "回复"),
        Phrase("bdq", "不好意思", "回复"),
        Phrase("hy", "好的", "回复"),
        Phrase("hd", "收到", "工作"),
        Phrase("qqr", "请确认", "工作"),
        Phrase("kxy", "可以吗", "日常"),
        Phrase("wmq", "我们去吧", "日常"),
        Phrase("cs", "城市", "日常"),
        Phrase("jyt", "教育", "学习"),
        Phrase("xx", "学习", "学习"),
        Phrase("zl", "资料", "学习"),
        Phrase("gzy", "工作日", "工作"),
        Phrase("js", "结束", "工作")
    )

    /** 展开缩写 */
    fun expand(abbrev: String, userDb: DictionaryDatabase): List<String> {
        val out = mutableListOf<String>()
        // 用户自定义优先
        userDb.readableDatabase.rawQuery(
            "SELECT content FROM ${DictionaryDatabase.T_USER_PHRASE} WHERE abbrev=?",
            arrayOf(abbrev)
        ).use { c -> while (c.moveToNext()) out.add(c.getString(0)) }
        // 内置短语
        builtin.filter { it.abbrev == abbrev }.forEach { out.add(it.content) }
        return out.distinct()
    }

    /** 按分类获取 */
    fun byCategory(cat: String): List<Phrase> = builtin.filter { it.category == cat }

    /** 添加用户自定义短语 */
    fun addUserPhrase(db: DictionaryDatabase, abbrev: String, content: String) {
        db.writableDatabase.execSQL(
            "INSERT OR REPLACE INTO ${DictionaryDatabase.T_USER_PHRASE}(abbrev,content,count) VALUES(?,?,0)",
            arrayOf(abbrev, content)
        )
    }
}
