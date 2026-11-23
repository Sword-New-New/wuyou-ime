package com.wuyouai.ime.dictionary

import android.content.Context
import android.util.Log
import com.wuyouai.ime.storage.DictionaryDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 基础词库导入器。
 * 首次启动从 assets/dict 导入完整离线词库（单字 6663 + 词组 336900）。
 * 采用分批事务批量插入（每 2 万条提交一次），避免单事务过大导致卡顿或内存暴涨。
 */
object DictionaryImporter {

    private const val TAG = "DictImporter"
    private const val PREF = "dict_meta"
    private const val KEY_IMPORTED = "base_imported"
    private const val KEY_VERSION = "base_version"

    /** 词库版本：词表更新时递增，触发重新导入 */
    private const val ASSET_VERSION = 2

    /** 每批提交的行数：兼顾速度与单次事务内存占用 */
    private const val BATCH = 20_000

    suspend fun ensureImported(context: Context): Boolean = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        if (prefs.getInt(KEY_VERSION, -1) >= ASSET_VERSION &&
            prefs.getBoolean(KEY_IMPORTED, false)
        ) {
            return@withContext true
        }
        try {
            // 版本升级时先清掉旧的基础词库，避免新旧混杂
            val db = DictionaryDatabase.get(context).writableDatabase
            db.beginTransaction()
            try {
                db.delete(DictionaryDatabase.T_CHAR, null, null)
                db.delete(DictionaryDatabase.T_WORD, null, null)
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }

            val chars = importChars(context)
            val words = importWords(context)
            prefs.edit()
                .putBoolean(KEY_IMPORTED, true)
                .putInt(KEY_VERSION, ASSET_VERSION)
                .apply()
            Log.i(TAG, "基础词库导入完成：单字 $chars，词组 $words")
            true
        } catch (e: Exception) {
            Log.e(TAG, "词库导入失败", e)
            false
        }
    }

    private fun importChars(context: Context): Int {
        val db = DictionaryDatabase.get(context).writableDatabase
        var count = 0
        db.beginTransaction()
        try {
            db.compileStatement(
                "INSERT OR REPLACE INTO ${DictionaryDatabase.T_CHAR}(char,pinyin,rank) VALUES(?,?,?)"
            ).use { stmt ->
                BufferedReader(
                    InputStreamReader(context.assets.open("dict/chars.txt"), Charsets.UTF_8)
                ).use { reader ->
                    var line = reader.readLine()
                    while (line != null) {
                        val parts = line.split('\t')
                        if (parts.size >= 3) {
                            stmt.bindString(1, parts[0])
                            stmt.bindString(2, parts[1])
                            stmt.bindLong(3, parts[2].toLongOrNull() ?: 9999L)
                            stmt.executeInsert()
                            count++
                        }
                        line = reader.readLine()
                    }
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return count
    }

    private fun importWords(context: Context): Int {
        val db = DictionaryDatabase.get(context).writableDatabase
        var count = 0
        var inTx = false
        db.beginTransaction()
        inTx = true
        try {
            db.compileStatement(
                "INSERT OR REPLACE INTO ${DictionaryDatabase.T_WORD}(word,pinyin,freq) VALUES(?,?,?)"
            ).use { stmt ->
                BufferedReader(
                    InputStreamReader(context.assets.open("dict/words.txt"), Charsets.UTF_8)
                ).use { reader ->
                    var line = reader.readLine()
                    while (line != null) {
                        val parts = line.split('\t')
                        if (parts.size >= 3) {
                            stmt.bindString(1, parts[0])
                            stmt.bindString(2, parts[1])
                            stmt.bindLong(3, parts[2].toLongOrNull() ?: 0L)
                            stmt.executeInsert()
                            count++
                            // 分批提交，避免超长事务
                            if (count % BATCH == 0) {
                                db.setTransactionSuccessful()
                                db.endTransaction()
                                inTx = false
                                db.beginTransaction()
                                inTx = true
                            }
                        }
                        line = reader.readLine()
                    }
                }
            }
            db.setTransactionSuccessful()
        } finally {
            if (inTx) db.endTransaction()
        }
        return count
    }

    fun exportUserWords(context: Context): String {
        val db = DictionaryDatabase.get(context).readableDatabase
        val sb = StringBuilder()
        db.rawQuery(
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
}
