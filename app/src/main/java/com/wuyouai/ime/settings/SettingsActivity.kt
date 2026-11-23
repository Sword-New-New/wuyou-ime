package com.wuyouai.ime.settings

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.wuyouai.ime.R
import com.wuyouai.ime.personalization.UserModel
import com.wuyouai.ime.storage.DictionaryDatabase
import kotlinx.coroutines.launch

/**
 * 设置中心主页。所有开关即时写入 DataStore，键盘读取后即时生效。
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var settings: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = SettingsManager.get(this)

        // 首次启动：尚未完成协议确认时，先进引导页。
        // 用 SharedPreferences 同步读取，避免 DataStore 异步导致的判定延迟。
        val prefs = getSharedPreferences("wuyou_boot", MODE_PRIVATE)
        val agreed = prefs.getBoolean("agreed", false)
        if (!agreed) {
            startActivity(Intent(this, com.wuyouai.ime.welcome.WelcomeActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_settings)

        // 启用输入法
        findViewById<android.view.View>(R.id.btn_enable_ime).setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        // 设为默认：跳转到输入法设置页，用户在其中选择
        findViewById<android.view.View>(R.id.btn_set_default).setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            Toast.makeText(this, "请在列表中点击\"无忧输入法\"，然后勾选\"默认使用\"", Toast.LENGTH_LONG).show()
        }
        // 词库管理
        findViewById<android.view.View>(R.id.btn_dict).setOnClickListener {
            startActivity(Intent(this, DictionaryActivity::class.java))
        }
        // 主题
        findViewById<android.view.View>(R.id.btn_theme).setOnClickListener {
            startActivity(Intent(this, ThemeActivity::class.java))
        }
        // 隐私说明
        findViewById<android.view.View>(R.id.btn_privacy).setOnClickListener {
            startActivity(Intent(this, com.wuyouai.ime.privacy.PrivacyActivity::class.java))
        }
        // 官方网站（交给系统浏览器，应用本身无网络权限）
        findViewById<android.view.View>(R.id.btn_official).setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(OFFICIAL_SITE)))
            } catch (e: Exception) {
                Toast.makeText(this, OFFICIAL_SITE, Toast.LENGTH_LONG).show()
            }
        }
        // 清除学习数据
        findViewById<android.view.View>(R.id.btn_clear_data).setOnClickListener {
            UserModel.get(this).clearLearningData()
            Toast.makeText(this, "个人学习数据已清除", Toast.LENGTH_SHORT).show()
        }

        bindSwitch(R.id.sw_sound, settings.sound, SettingsManager.KEY_SOUND)
        bindSwitch(R.id.sw_vibrate, settings.vibrate, SettingsManager.KEY_VIBRATE)
        bindSwitch(R.id.sw_fuzzy, settings.fuzzy, SettingsManager.KEY_FUZZY)
        bindSwitch(R.id.sw_correct, settings.correct, SettingsManager.KEY_CORRECT)
        bindSwitch(R.id.sw_simplified, settings.simplified, SettingsManager.KEY_SIMPLIFIED)
        bindSwitch(R.id.sw_associate, settings.associate, SettingsManager.KEY_ASSOCIATE)
        bindSwitch(R.id.sw_personalize, settings.personalize, SettingsManager.KEY_PERSONALIZE)
        bindSwitch(R.id.sw_context, settings.contextPredict, SettingsManager.KEY_CONTEXT_PREDICT)
        bindSwitch(R.id.sw_privacy, settings.privacyMode, SettingsManager.KEY_PRIVACY_MODE)
        bindSwitch(R.id.sw_clipboard, settings.clipboardHistory, SettingsManager.KEY_CLIPBOARD_HISTORY)
    }

    private fun bindSwitch(id: Int, flow: kotlinx.coroutines.flow.Flow<Boolean>, key: androidx.datastore.preferences.core.Preferences.Key<Boolean>) {
        val sw = findViewById<Switch>(id)
        lifecycleScope.launch {
            flow.collect { value -> sw.isChecked = value }
        }
        sw.setOnCheckedChangeListener { _, checked ->
            settings.set(key, checked)
        }
    }

    companion object {
        /** 官方网站：由系统浏览器打开，本应用自身不访问网络 */
        private const val OFFICIAL_SITE = "https://wuyouai.gemigo.app/"
    }
}
