package com.wuyouai.ime

import android.app.Application
import com.wuyouai.ime.dictionary.DictionaryImporter
import com.wuyouai.ime.settings.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 应用入口。启动时初始化设置缓存并在后台导入基础词库（离线，随 APK 交付）。
 */
class WuyouApplication : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        val settings = SettingsManager.get(this)
        settings.startCaching()
        appScope.launch {
            DictionaryImporter.ensureImported(this@WuyouApplication)
        }
    }
}
