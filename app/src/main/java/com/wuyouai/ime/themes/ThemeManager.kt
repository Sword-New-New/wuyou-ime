package com.wuyouai.ime.themes

/**
 * 主题系统：内置 6 套克制、清晰的纯色质感主题。
 * 颜色即时生效，切换无重启。
 */
data class KeyboardTheme(
    val id: String,
    val name: String,
    val bg: Int,               // 键盘背景底板
    val keyBg: Int,            // 普通按键背景
    val keyText: Int,          // 按键文字
    val specialKeyBg: Int,     // 功能键背景
    val accent: Int,           // 强调色（回车键/首选词）
    val candidateText: Int,    // 候选文字
    val candidateActive: Int,  // 高亮候选
    val candidateBarBg: Int,   // 候选栏背景
    val isDark: Boolean,

    /** 扩展色值（带默认值以保持向后兼容） */
    val keyShadow: Int = 0xFFBFC6D2.toInt(),
    val specialShadow: Int = 0xFFAEB6C5.toInt(),
    val hairline: Int = 0xFFD4DAE3.toInt(),
    val accentText: Int = 0xFFFFFFFF.toInt(),
    val pressedBg: Int = 0xFFDCE4F5.toInt(),
    val popupBg: Int = 0xFFFFFFFF.toInt()
)

object ThemeManager {

    val themes: List<KeyboardTheme> = listOf(
        KeyboardTheme(
            id = "pure_white",
            name = "素瓷白",
            bg = 0xFFE6E9EF.toInt(),
            keyBg = 0xFFFFFFFF.toInt(),
            keyText = 0xFF1A1E26.toInt(),
            specialKeyBg = 0xFFD5DBE5.toInt(),
            accent = 0xFF2F5FD0.toInt(),
            candidateText = 0xFF1A1E26.toInt(),
            candidateActive = 0xFF2F5FD0.toInt(),
            candidateBarBg = 0xFFECEFF4.toInt(),
            isDark = false,
            keyShadow = 0xFFBFC6D2.toInt(),
            specialShadow = 0xFFAEB6C5.toInt(),
            hairline = 0xFFD4DAE3.toInt(),
            accentText = 0xFFFFFFFF.toInt(),
            pressedBg = 0xFFDCE4F5.toInt(),
            popupBg = 0xFFFFFFFF.toInt()
        ),
        KeyboardTheme(
            id = "deep_black",
            name = "墨玉黑",
            bg = 0xFF14161B.toInt(),
            keyBg = 0xFF2A2D35.toInt(),
            keyText = 0xFFF2F4F8.toInt(),
            specialKeyBg = 0xFF1F2229.toInt(),
            accent = 0xFF4F82F0.toInt(),
            candidateText = 0xFFEAECEF.toInt(),
            candidateActive = 0xFF4F82F0.toInt(),
            candidateBarBg = 0xFF191C22.toInt(),
            isDark = true,
            keyShadow = 0xFF0C0D11.toInt(),
            specialShadow = 0xFF0C0D11.toInt(),
            hairline = 0xFF262A33.toInt(),
            accentText = 0xFFFFFFFF.toInt(),
            pressedBg = 0xFF3D4454.toInt(),
            popupBg = 0xFF3A3F4B.toInt()
        ),
        KeyboardTheme(
            id = "minimal_gray",
            name = "雾银灰",
            bg = 0xFFDFE3E8.toInt(),
            keyBg = 0xFFFFFFFF.toInt(),
            keyText = 0xFF1E232C.toInt(),
            specialKeyBg = 0xFFCFD5DF.toInt(),
            accent = 0xFF3E67B8.toInt(),
            candidateText = 0xFF1E232C.toInt(),
            candidateActive = 0xFF3E67B8.toInt(),
            candidateBarBg = 0xFFE6E9EE.toInt(),
            isDark = false,
            keyShadow = 0xFFB8C0CC.toInt(),
            specialShadow = 0xFFA6AFBE.toInt(),
            hairline = 0xFFCCD2DC.toInt(),
            accentText = 0xFFFFFFFF.toInt(),
            pressedBg = 0xFFD8E1F0.toInt(),
            popupBg = 0xFFFFFFFF.toInt()
        ),
        KeyboardTheme(
            id = "fresh_blue",
            name = "晴空蓝",
            bg = 0xFFDCE7F5.toInt(),
            keyBg = 0xFFFFFFFF.toInt(),
            keyText = 0xFF173252.toInt(),
            specialKeyBg = 0xFFC8DAEE.toInt(),
            accent = 0xFF2B6CB0.toInt(),
            candidateText = 0xFF173252.toInt(),
            candidateActive = 0xFF2B6CB0.toInt(),
            candidateBarBg = 0xFFE4EEF9.toInt(),
            isDark = false,
            keyShadow = 0xFFAFC5DF.toInt(),
            specialShadow = 0xFF9EB7D4.toInt(),
            hairline = 0xFFC6D7EC.toInt(),
            accentText = 0xFFFFFFFF.toInt(),
            pressedBg = 0xFFD3E5FA.toInt(),
            popupBg = 0xFFFFFFFF.toInt()
        ),
        KeyboardTheme(
            id = "cream_soft",
            name = "宣纸暖白",
            bg = 0xFFEAE2D6.toInt(),
            keyBg = 0xFFFDFBF7.toInt(),
            keyText = 0xFF372F27.toInt(),
            specialKeyBg = 0xFFDDD3C3.toInt(),
            accent = 0xFF9A6B46.toInt(),
            candidateText = 0xFF372F27.toInt(),
            candidateActive = 0xFF9A6B46.toInt(),
            candidateBarBg = 0xFFF0E9DE.toInt(),
            isDark = false,
            keyShadow = 0xFFC8BCAE.toInt(),
            specialShadow = 0xFFBBAEA0.toInt(),
            hairline = 0xFFD9CFC0.toInt(),
            accentText = 0xFFFFFFFF.toInt(),
            pressedBg = 0xFFEFE3D5.toInt(),
            popupBg = 0xFFFDFBF7.toInt()
        ),
        KeyboardTheme(
            id = "dark_eye_care",
            name = "深松护眼",
            bg = 0xFF1B1E1A.toInt(),
            keyBg = 0xFF2E332C.toInt(),
            keyText = 0xFFDCDAC9.toInt(),
            specialKeyBg = 0xFF242822.toInt(),
            accent = 0xFF7D9B6A.toInt(),
            candidateText = 0xFFDCDAC9.toInt(),
            candidateActive = 0xFF7D9B6A.toInt(),
            candidateBarBg = 0xFF20241F.toInt(),
            isDark = true,
            keyShadow = 0xFF111410.toInt(),
            specialShadow = 0xFF111410.toInt(),
            hairline = 0xFF2D332B.toInt(),
            accentText = 0xFF121610.toInt(),
            pressedBg = 0xFF3D4539.toInt(),
            popupBg = 0xFF3C4339.toInt()
        )
    )

    fun byId(id: String): KeyboardTheme = themes.firstOrNull { it.id == id } ?: themes[0]

    fun nextAfter(currentId: String): KeyboardTheme {
        val idx = themes.indexOfFirst { it.id == currentId }
        return if (idx < 0) themes[0] else themes[(idx + 1) % themes.size]
    }

    /** 主题切换即时生效，无需重启服务 */
    fun applyTheme(id: String): KeyboardTheme = byId(id)
}
