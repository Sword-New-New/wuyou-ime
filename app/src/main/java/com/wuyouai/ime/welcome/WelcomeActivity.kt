package com.wuyouai.ime.welcome

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.wuyouai.ime.R
import com.wuyouai.ime.settings.SettingsActivity
import com.wuyouai.ime.settings.SettingsManager

/** 引导页总页数：6 页 */
private const val PAGE_COUNT = 6



/**
 * 首次启动引导，6 页：
 * 1 欢迎  2 协议确认  3 开始使用  4 启用输入法  5 官网  6 完成
 *
 * 三份法律文本各有独立入口，隐私相关开关单独列出，
 * 不使用「同意全部」这类捆绑式同意。
 */
class WelcomeActivity : AppCompatActivity() {

    private lateinit var pager: ViewPager2
    private lateinit var btn: Button
    private lateinit var dots: LinearLayout
    private val dotViews = mutableListOf<View>()

    /** 第二页单独的三项确认状态（不捆绑） */
    private var agreePrivacy = false
    private var agreeTerms = false
    private var agreeDisclaimer = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome)

        pager = findViewById(R.id.welcome_pager)
        btn = findViewById(R.id.welcome_btn)
        dots = findViewById(R.id.dots_container)

        pager.adapter = PageAdapter()
        pager.isUserInputEnabled = true
        pager.offscreenPageLimit = 1

        buildDots()
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateUi(position)
            }
        })

        btn.setOnClickListener {
            val cur = pager.currentItem
            when {
                cur < PAGE_COUNT - 1 -> pager.currentItem = cur + 1
                else -> finishGuide()
            }
        }

        updateUi(0)
    }

    private fun buildDots() {
        dots.removeAllViews()
        dotViews.clear()
        val size = (6 * resources.displayMetrics.density).toInt()
        val gap = (7 * resources.displayMetrics.density).toInt()
        for (i in 0 until PAGE_COUNT) {
            val v = View(this)
            val lp = LinearLayout.LayoutParams(size, size)
            lp.marginStart = gap
            lp.marginEnd = gap
            v.layoutParams = lp
            dots.addView(v)
            dotViews.add(v)
        }
    }

    private fun updateUi(position: Int) {
        dotViews.forEachIndexed { i, v ->
            val active = i == position
            v.setBackgroundColor(if (active) 0xFF2F5C8F.toInt() else 0xFFD3DAE3.toInt())
            val s = if (active) (8 * resources.displayMetrics.density).toInt()
                    else (6 * resources.displayMetrics.density).toInt()
            val lp = v.layoutParams
            if (lp is LinearLayout.LayoutParams) {
                lp.width = s
                lp.height = s
                v.layoutParams = lp
            }
            v.requestLayout()
        }

        btn.text = when {
            position < PAGE_COUNT - 1 -> "下一步"
            else -> "开始使用"
        }
    }

    /** 完成引导：写入已同意标记，进入设置页 */
    private fun finishGuide() {
        // 三个独立确认项必须都勾选才允许进入。
        // 不做「同意全部」的捆绑同意：任何一项未同意，就不能继续。
        if (!agreePrivacy || !agreeTerms || !agreeDisclaimer) {
            android.widget.Toast.makeText(
                this,
                "请分别阅读并勾选三项协议后再继续",
                android.widget.Toast.LENGTH_LONG
            ).show()
            pager.currentItem = 1
            return
        }

        // 同步写入，保证下次启动立即生效（DataStore 是异步的，这里不适合）
        getSharedPreferences("wuyou_boot", MODE_PRIVATE).edit()
            .putBoolean("agreed", true)
            .putString("agreed_version", "2.0")
            .putLong("agreed_at", System.currentTimeMillis())
            .apply()

        // 同时写入 DataStore，供应用内其它位置读取
        val s = SettingsManager.get(this)
        s.set(SettingsManager.KEY_AGREED, true)
        s.set(SettingsManager.KEY_AGREED_VERSION, "2.0")
        s.set(SettingsManager.KEY_AGREED_AT, System.currentTimeMillis())

        startActivity(Intent(this, SettingsActivity::class.java))
        finish()
    }

    // ---------------- 页面构建 ----------------

    private inner class PageAdapter : RecyclerView.Adapter<PageAdapter.Holder>() {

        inner class Holder(val v: View) : RecyclerView.ViewHolder(v)

        override fun getItemCount(): Int = PAGE_COUNT

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val sv = ScrollView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                isFillViewport = true
            }
            val col = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(28), dp(48), dp(28), dp(24))
            }
            sv.addView(col, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            return Holder(sv)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val col = ((holder.v as ScrollView).getChildAt(0) as LinearLayout)
            col.removeAllViews()
            when (position) {
                0 -> buildWelcome(col)
                1 -> buildAgreements(col)
                2 -> buildIntro(col)
                3 -> buildEnable(col)
                4 -> buildOfficial(col)
                5 -> buildFinish(col)
            }
        }

        private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    }

    private fun title(col: LinearLayout, text: String) {
        col.addView(TextView(this).apply {
            this.text = text
            textSize = 25f
            setTextColor(0xFF12141A.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(14))
        })
    }

    private fun body(col: LinearLayout, text: String) {
        col.addView(TextView(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(0xFF5B6470.toInt())
            gravity = Gravity.CENTER
            setLineSpacing(dp(6).toFloat(), 1f)
            setPadding(0, 0, 0, dp(10))
        })
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // 第 1 页：大欢迎
    private fun buildWelcome(col: LinearLayout) {
        col.addView(ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher_foreground)
            layoutParams = LinearLayout.LayoutParams(dp(120), dp(120))
        })
        val sp = Space(this, dp(18))
        col.addView(sp)
        title(col, "欢迎使用无忧输入法")
        body(col, "让输入回归简单，\n让智能留在本地，\n让隐私始终属于用户。")
        col.addView(TextView(this).apply {
            text = "完全离线 · 无网络权限 · 词库内置"
            textSize = 12f
            setTextColor(0xFF8A9099.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(20), 0, 0)
        })
    }

    // 第 2 页：三份协议独立入口（不捆绑）
    private fun buildAgreements(col: LinearLayout) {
        title(col, "请阅读以下文件")
        body(col, "在开始使用之前，请分别阅读并确认以下内容。\n每一项都需要你单独同意。")

        docEntry(col, "隐私政策", "应用如何处理你的输入内容与本地数据", "privacy.md", agreePrivacy) {
            agreePrivacy = it
        }
        docEntry(col, "用户使用协议", "使用本应用时需要遵守的约定", "terms.md", agreeTerms) {
            agreeTerms = it
        }
        docEntry(col, "免责声明", "输入结果的辅助性质与责任范围", "disclaimer.md", agreeDisclaimer) {
            agreeDisclaimer = it
        }

        col.addView(TextView(this).apply {
            text = "不同意非必要的数据处理或权限申请时，\n你可以选择不使用本应用，我们不会以捆绑同意的方式强迫你接受。"
            textSize = 11.5f
            setTextColor(0xFF9AA0AA.toInt())
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(18), dp(4), 0)
        })
    }

    /** 一个文档条目：标题 + 摘要 + 「查看全文」+ 独立勾选 */
    private fun docEntry(
        col: LinearLayout,
        name: String,
        summary: String,
        asset: String,
        checked: Boolean,
        onChange: (Boolean) -> Unit
    ) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(12))
            setBackgroundResource(R.drawable.bg_card)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(6), 0, dp(6)) }
        }

        card.addView(TextView(this).apply {
            text = name
            textSize = 15f
            setTextColor(0xFF1A1D23.toInt())
        })
        card.addView(TextView(this).apply {
            text = summary
            textSize = 12f
            setTextColor(0xFF9AA0AA.toInt())
            setPadding(0, dp(3), 0, dp(8))
        })

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val viewBtn = TextView(this).apply {
            text = "查看全文 ›"
            textSize = 13f
            setTextColor(0xFF2F5C8F.toInt())
            setPadding(0, dp(4), 0, dp(4))
            setOnClickListener {
                startActivity(Intent(this@WelcomeActivity, DocActivity::class.java)
                    .putExtra(DocActivity.EXTRA_ASSET, asset)
                    .putExtra(DocActivity.EXTRA_TITLE, name))
            }
        }
        row.addView(viewBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val check = android.widget.CheckBox(this).apply {
            this.text = "我已阅读并同意"
            textSize = 12.5f
            isChecked = checked
            setTextColor(0xFF5B6470.toInt())
            setOnCheckedChangeListener { _, isChecked -> onChange(isChecked) }
        }
        row.addView(check)

        card.addView(row)
        col.addView(card)
    }

    // 第 3 页：开始使用（介绍核心特性）
    private fun buildIntro(col: LinearLayout) {
        title(col, "这些能力都在本地")
        body(col, "无需联网，飞行模式下功能完整可用。")

        feature(col, "33.7 万词本地词库", "内置真实词表，长句输入按词频与上下文择优")
        feature(col, "拼音纠错与模糊音", "少打字母、zh/z 不分也能出正确候选")
        feature(col, "本地个性化学习", "只记录在你的设备上，从不外传")
        feature(col, "模拟密码框保护", "系统标记为密码的输入框，完全不学习")
    }

    private fun feature(col: LinearLayout, head: String, desc: String) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setBackgroundResource(R.drawable.bg_card)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(5), 0, dp(5)) }
        }
        card.addView(TextView(this).apply {
            text = head
            textSize = 14.5f
            setTextColor(0xFF1A1D23.toInt())
        })
        card.addView(TextView(this).apply {
            text = desc
            textSize = 12.5f
            setTextColor(0xFF8A9099.toInt())
            setPadding(0, dp(3), 0, 0)
        })
        col.addView(card)
    }

    // 第 4 页：启用输入法
    private fun buildEnable(col: LinearLayout) {
        title(col, "两步启用输入法")
        body(col, "请按顺序完成，之后即可用无忧输入法打字。")

        step(col, "第 1 步", "启用无忧输入法", "在系统设置中勾选本输入法") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        step(col, "第 2 步", "设为默认输入法", "在「当前输入法」中选择无忧输入法") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }

        col.addView(TextView(this).apply {
            text = "也可以稍后在应用内首页完成这两步。"
            textSize = 12f
            setTextColor(0xFF9AA0AA.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, 0)
        })
    }

    private fun step(col: LinearLayout, index: String, head: String, desc: String, onClick: () -> Unit) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundResource(R.drawable.bg_card)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(6), 0, dp(6)) }
            setOnClickListener { onClick() }
        }
        card.addView(TextView(this).apply {
            text = index
            textSize = 12f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            setBackgroundColor(0xFF2F5C8F.toInt())
            setPadding(dp(8), dp(4), dp(8), dp(4))
        })
        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        info.addView(TextView(this).apply {
            text = head
            textSize = 15f
            setTextColor(0xFF1A1D23.toInt())
        })
        info.addView(TextView(this).apply {
            text = desc
            textSize = 12f
            setTextColor(0xFF8A9099.toInt())
            setPadding(0, dp(2), 0, 0)
        })
        card.addView(info, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(TextView(this).apply {
            text = "›"
            textSize = 20f
            setTextColor(0xFFC4C9D2.toInt())
        })
        col.addView(card)
    }

    // 第 5 页：官网
    private fun buildOfficial(col: LinearLayout) {
        title(col, "官方网站")
        body(col, "了解更新动态、使用说明与反馈渠道。")

        col.addView(TextView(this).apply {
            text = "https://wuyouai.gemigo.app/"
            textSize = 15f
            setTextColor(0xFF2F5C8F.toInt())
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(16), dp(12), dp(16))
            setBackgroundResource(R.drawable.bg_card)
            setOnClickListener { openOfficial() }
        })

        col.addView(TextView(this).apply {
            text = "该链接由系统浏览器打开。\n无忧输入法自身不持有网络权限，不会代为发起任何网络请求。"
            textSize = 12f
            setTextColor(0xFF9AA0AA.toInt())
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(16), dp(6), 0)
        })
    }

    private fun openOfficial() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(OFFICIAL_SITE)))
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, OFFICIAL_SITE, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    // 第 6 页：完成
    private fun buildFinish(col: LinearLayout) {
        title(col, "一切就绪")
        body(col, "无忧输入法已经准备好。\n现在开始，让输入更简单一点。")

        col.addView(TextView(this).apply {
            text = "本地输入 · 隐私优先 · 永不联网"
            textSize = 12.5f
            setTextColor(0xFF8A9099.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(24), 0, 0)
        })
    }

    companion object {
        private const val OFFICIAL_SITE = "https://wuyouai.gemigo.app/"
    }
}

/** 空占位 View，避免外部依赖 */
private class Space(context: android.content.Context, private val h: Int) : View(context) {
    init {
        layoutParams = ViewGroup.LayoutParams(1, h)
    }
}
