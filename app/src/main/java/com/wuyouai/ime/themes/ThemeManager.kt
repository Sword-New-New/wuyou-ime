package com.wuyouai.ime.themes

import android.graphics.Color

/**
 * 主题系统：内置 6 套完整主题 + 自定义参数。
 * 颜色即时生效，切换无重启。
 */
data class KeyboardTheme(
    val id: String,
    val name: String,
    val bg: Int,            // 键盘背景
    val keyBg: Int,         // 普通按键背景
    val keyText: Int,       // 按键文字
    val specialKeyBg: Int,  // 功能键背景
    val accent: Int,        // 强调色（低饱和蓝）
    val candidateText: Int, // 候选文字
    val candidateActive: Int, // 高亮候选
    val candidateBarBg: Int,  // 候选栏背景
    val isDark: Boolean,

    /** 是否为「液态玻璃」风格：键帽半透明 + 高光 + 内部折射 */
    val liquidGlass: Boolean = false,
    /** 玻璃背景墙：底部渐变的两端颜色（决定透过玻璃看到的画面） */
    val glassWallTop: Int = 0,
    val glassWallBottom: Int = 0
)

object ThemeManager {

    val themes: List<KeyboardTheme> = listOf(
        KeyboardTheme("pure_white", "纯白极简",
            Color.WHITE, Color.WHITE, 0xFF1A1A1A.toInt(), 0xFFF0F0F2.toInt(),
            0xFF4A6FA5.toInt(), 0xFF1A1A1A.toInt(), 0xFF4A6FA5.toInt(), 0xFFFAFAFA.toInt(), false),
        KeyboardTheme("deep_black", "深空黑",
            0xFF121212.toInt(), 0xFF2A2A2C.toInt(), 0xFFF5F5F5.toInt(), 0xFF232325.toInt(),
            0xFF7A9BC4.toInt(), 0xFFE8E8E8.toInt(), 0xFF7A9BC4.toInt(), 0xFF1A1A1A.toInt(), true),
        KeyboardTheme("minimal_gray", "极简灰",
            0xFFEFEFF1.toInt(), 0xFFFFFFFF.toInt(), 0xFF222222.toInt(), 0xFFE2E2E5.toInt(),
            0xFF5B7DA8.toInt(), 0xFF222222.toInt(), 0xFF5B7DA8.toInt(), 0xFFE8E8EA.toInt(), false),
        KeyboardTheme("fresh_blue", "清新蓝",
            0xFFEAF1FA.toInt(), 0xFFFFFFFF.toInt(), 0xFF1B3A5C.toInt(), 0xFFD6E4F5.toInt(),
            0xFF3D6EA8.toInt(), 0xFF1B3A5C.toInt(), 0xFF3D6EA8.toInt(), 0xFFDDE9F7.toInt(), false),
        KeyboardTheme("cream_soft", "米白柔和",
            0xFFF7F3EC.toInt(), 0xFFFFFCF7.toInt(), 0xFF4A4238.toInt(), 0xFFEDE6DA.toInt(),
            0xFF9C7B5E.toInt(), 0xFF4A4238.toInt(), 0xFF9C7B5E.toInt(), 0xFFF1EBE1.toInt(), false),
        KeyboardTheme("dark_eye_care", "深色护眼",
            0xFF1E1F1B.toInt(), 0xFF33342E.toInt(), 0xFFD8D5C4.toInt(), 0xFF2A2B26.toInt(),
            0xFFA8B58A.toInt(), 0xFFD0CDBB.toInt(), 0xFFA8B58A.toInt(), 0xFF25261F.toInt(), true)

        // ---- 液态玻璃主题暂时下架 ----
        // 说明：绘制实现（KeyboardView.drawLiquidGlass）仍完整保留，
        // 只是不再出现在主题列表里，避免用户选到后出现异常。
        // 需要恢复时，把下面两条 KeyboardTheme 追加回列表即可。
        //
        // KeyboardTheme(id = "liquid_glass_light", name = "液态玻璃 · 浅", ...)
        // KeyboardTheme(id = "liquid_glass_dark",  name = "液态玻璃 · 深", ...)
    )

    fun byId(id: String): KeyboardTheme = themes.firstOrNull { it.id == id } ?: themes[0]

    /** 主题切换即时生效，无需重启服务 */
    fun applyTheme(id: String): KeyboardTheme = byId(id)
}
