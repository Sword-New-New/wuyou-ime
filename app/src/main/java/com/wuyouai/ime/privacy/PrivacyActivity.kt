package com.wuyouai.ime.privacy

import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * 隐私保护说明。如实、清晰地说明数据如何处理。
 */
class PrivacyActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tv = TextView(this).apply {
            setPadding(48, 48, 48, 48)
            textSize = 15f
            text = PRIVACY_TEXT
        }
        setContentView(ScrollView(this).apply { addView(tv) })
    }

    companion object {
        val PRIVACY_TEXT = """
无忧输入法 V2.0 —— 隐私保护说明

【核心承诺】
智能不必上云，隐私无需妥协。

【不联网】
本应用未申请 android.permission.INTERNET 权限。这意味着从系统层面，应用在任何情况下都无法访问网络，不存在把你的输入上传到任何服务器的可能。可在「设置-应用-无忧输入法-权限」中验证：没有网络权限。

【无第三方 SDK】
应用不集成任何广告、统计、远程日志、推送 SDK。不收集设备标识、位置、通讯录、剪贴板（后台）等任何数据。

【输入内容的处理】
作为输入法，应用在技术上必然会在内存中接触到你正在输入的内容——这是输入法的基本工作原理，任何输入法都无法回避。我们的设计原则是：
- 只在内存中处理当前这一次输入所需的内容，用完即释放；
- 不上传、不同步、不写入日志；
- 密码框、可见密码框、标记为敏感的输入框：完全不学习、完全不记录；
- 隐私模式开启时：所有输入框都不学习、不记录。

【本地数据】
- 个人词库、用户词频、学习记录、设置：全部保存在应用私有目录，操作系统隔离，其他应用无法读取；
- 基础词库（字/词）随 APK 一起内置，首次使用无需联网下载；
- 你可以随时在「词库管理」中导出、在「设置-清除学习数据」中彻底删除个人学习数据。

【剪贴板】
剪贴板历史默认关闭。开启后，仅在键盘处于前台、你主动查看时才读取系统剪贴板，绝不在后台或键盘不可见时读取。

【无障碍服务】
本应用不申请无障碍服务权限，不通过无障碍监控任何应用的内容。

【验证方式】
- 检查权限：设置-应用-无忧输入法，应无任何敏感权限；
- 检查联网：飞行模式下所有功能照常可用；
- 开发者信息：无忧输入法开发团队。
- 官方网站：https://wuyouai.gemigo.app/
        """.trimIndent()
    }
}
