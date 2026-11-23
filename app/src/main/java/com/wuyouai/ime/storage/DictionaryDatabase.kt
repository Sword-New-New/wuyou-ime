package com.wuyouai.ime.storage

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * 本地词库数据库（SQLite）。
 * 基础词库（字/词）随 APK assets 交付，首次启动导入；
 * 用户个人数据（用户词、学习记录）存独立表，与基础词库分离。
 * 数据库位于应用私有目录，不对其他应用开放。
 */
class DictionaryDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        const val DB_NAME = "wuyou_dict.db"
        const val DB_VERSION = 1

        // 基础单字表
        const val T_CHAR = "base_char"
        // 基础词组表
        const val T_WORD = "base_word"
        // 用户个人词表（学习产生）
        const val T_USER_WORD = "user_word"
        // 用户自定义短语（缩写展开）
        const val T_USER_PHRASE = "user_phrase"

        @Volatile
        private var instance: DictionaryDatabase? = null

        fun get(context: Context): DictionaryDatabase =
            instance ?: synchronized(this) {
                instance ?: DictionaryDatabase(context.applicationContext).also { instance = it }
            }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS $T_CHAR (
                _id INTEGER PRIMARY KEY AUTOINCREMENT,
                char TEXT NOT NULL,
                pinyin TEXT NOT NULL,
                rank INTEGER NOT NULL DEFAULT 9999
            )"""
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_char_pinyin ON $T_CHAR(pinyin)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_char_rank ON $T_CHAR(rank)")

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS $T_WORD (
                _id INTEGER PRIMARY KEY AUTOINCREMENT,
                word TEXT NOT NULL,
                pinyin TEXT NOT NULL,
                freq INTEGER NOT NULL DEFAULT 0
            )"""
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_word_pinyin ON $T_WORD(pinyin)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_word_freq ON $T_WORD(freq)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_word_text ON $T_WORD(word)")

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS $T_USER_WORD (
                _id INTEGER PRIMARY KEY AUTOINCREMENT,
                word TEXT NOT NULL UNIQUE,
                pinyin TEXT NOT NULL,
                count INTEGER NOT NULL DEFAULT 1,
                last_used INTEGER NOT NULL DEFAULT 0
            )"""
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_uw_pinyin ON $T_USER_WORD(pinyin)")

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS $T_USER_PHRASE (
                _id INTEGER PRIMARY KEY AUTOINCREMENT,
                abbrev TEXT NOT NULL,
                content TEXT NOT NULL,
                count INTEGER NOT NULL DEFAULT 0
            )"""
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_up_abbrev ON $T_USER_PHRASE(abbrev)")

        // 上下文搭配表：前词\u0001候选词 -> 出现次数
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS user_context (
                pair TEXT PRIMARY KEY,
                count INTEGER NOT NULL DEFAULT 0
            )"""
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // V2.0 首版，暂无升级路径
    }

    fun wipeUserTables() {
        writableDatabase.apply {
            delete(T_USER_WORD, null, null)
            delete(T_USER_PHRASE, null, null)
            delete("user_context", null, null)
        }
    }
}
