package com.wuyouai.ime.clipboard

import android.content.ClipboardManager
import android.content.Context
import com.wuyouai.ime.settings.SettingsManager

/**
 * 本地剪贴板管理。
 * 只在键盘可见时读取系统剪贴板（前台、用户主动），绝不在后台偷偷读取。
 * 历史默认关闭，需用户在设置中开启。
 */
class LocalClipboard private constructor(private val context: Context) {

    companion object {
        @Volatile private var instance: LocalClipboard? = null
        fun get(context: Context): LocalClipboard =
            instance ?: synchronized(this) {
                instance ?: LocalClipboard(context.applicationContext).also { instance = it }
            }
        private const val MAX_HISTORY = 30
    }

    private val settings = SettingsManager.get(context)
    private val history = ArrayDeque<String>()
    private val pinned = mutableListOf<String>()
    private var lastSeen: String? = null

    val isEnabled: Boolean get() = settings.cachedClipboardHistory

    /** 键盘显示时同步一次剪贴板（仅前台） */
    fun syncFromSystem(): String? {
        if (!settings.cachedClipboardHistory) return null
        return try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            if (cm.hasPrimaryClip()) {
                val text = cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                if (!text.isNullOrBlank() && text != lastSeen) {
                    lastSeen = text
                    addToHistory(text)
                }
                text
            } else null
        } catch (_: Exception) { null }
    }

    private fun addToHistory(text: String) {
        history.removeAll { it == text }
        history.addFirst(text)
        while (history.size > MAX_HISTORY) history.removeLast()
    }

    fun getHistory(): List<String> = history.toList()
    fun getPinned(): List<String> = pinned.toList()

    fun pin(text: String) {
        if (!pinned.contains(text)) pinned.add(0, text)
    }

    fun unpin(text: String) { pinned.remove(text) }
    fun remove(text: String) { history.removeAll { it == text } }
    fun clearAll() { history.clear() }

    fun paste(text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("wuyou", text))
    }
}
