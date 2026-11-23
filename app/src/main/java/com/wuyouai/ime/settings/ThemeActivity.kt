package com.wuyouai.ime.settings

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.wuyouai.ime.themes.ThemeManager

/**
 * 外观主题：内置 6 套主题，点击即时切换并持久化。
 */
class ThemeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = SettingsManager.get(this)
        val scroll = android.widget.ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }
        layout.addView(TextView(this).apply {
            text = "选择主题（点击即时生效）"
            textSize = 18f
        })

        ThemeManager.themes.forEach { theme ->
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(32, 32, 32, 32)
                background = GradientDrawable().apply {
                    setColor(theme.bg)
                    cornerRadius = 24f
                    setStroke(3, theme.accent)
                }
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.setMargins(0, 24, 0, 0)
                layoutParams = lp
                // 色块预览
                addView(android.view.View(this@ThemeActivity).apply {
                    background = GradientDrawable().apply {
                        setColor(theme.keyBg)
                        cornerRadius = 12f
                        setStroke(2, theme.keyText)
                    }
                    val vlp = LinearLayout.LayoutParams(80, 80)
                    vlp.setMargins(0, 0, 32, 0)
                    layoutParams = vlp
                })
                addView(TextView(this@ThemeActivity).apply {
                    text = "${theme.name}\n${if (theme.isDark) "深色" else "浅色"}"
                    textSize = 16f
                    setTextColor(theme.keyText)
                })
                setOnClickListener {
                    settings.set(SettingsManager.KEY_THEME, theme.id)
                    settings.cachedTheme = theme.id
                    android.widget.Toast.makeText(
                        this@ThemeActivity, "已切换到：${theme.name}", android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
            layout.addView(item)
        }
        scroll.addView(layout)
        setContentView(scroll)
    }
}
