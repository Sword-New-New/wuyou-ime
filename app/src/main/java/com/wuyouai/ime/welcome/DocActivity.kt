package com.wuyouai.ime.welcome

import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * 文档全文查看页。
 * 内容来自 assets/docs 下的 md 文件，纯本地读取，无网络。
 */
class DocActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val asset = intent.getStringExtra(EXTRA_ASSET) ?: "privacy.md"
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "文档"
        if (supportActionBar != null) {
            supportActionBar!!.title = title
            supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        } else {
            setTitle(title)
        }

        val text = try {
            assets.open("docs/$asset").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            "文档读取失败：$asset"
        }

        val tv = TextView(this).apply {
            this.text = text
            textSize = 14.5f
            setLineSpacing(8f, 1f)
            setPadding(dp(20), dp(12), dp(20), dp(32))
            setTextIsSelectable(true)
        }

        // NoActionBar 主题下系统不画返回按钮，这里自建一个返回栏，
        // 否则用户进入文档后只能靠系统返回键退出。
        val back = TextView(this).apply {
            this.text = "‹ 返回"
            textSize = 15f
            setTextColor(0xFF2F5C8F.toInt())
            setPadding(dp(20), dp(14), dp(20), dp(14))
            setOnClickListener { finish() }
        }

        val col = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
        }
        col.addView(back)
        col.addView(ScrollView(this).apply { addView(tv) })

        setContentView(col)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_ASSET = "asset"
        const val EXTRA_TITLE = "title"
    }
}
