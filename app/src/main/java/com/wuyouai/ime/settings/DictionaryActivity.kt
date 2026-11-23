package com.wuyouai.ime.settings

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.wuyouai.ime.R
import com.wuyouai.ime.personalization.UserModel
import com.wuyouai.ime.storage.DictionaryDatabase

/**
 * 词库管理：查看基础词库规模、添加/导入/导出个人词库。
 */
class DictionaryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ctx = this
        val db = DictionaryDatabase.get(ctx)
        val userModel = UserModel.get(ctx)

        val scroll = android.widget.ScrollView(ctx)
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }

        // 基础词库统计
        var charCount = 0; var wordCount = 0; var userCount = 0
        db.readableDatabase.rawQuery("SELECT COUNT(*) FROM ${DictionaryDatabase.T_CHAR}", null).use { if (it.moveToFirst()) charCount = it.getInt(0) }
        db.readableDatabase.rawQuery("SELECT COUNT(*) FROM ${DictionaryDatabase.T_WORD}", null).use { if (it.moveToFirst()) wordCount = it.getInt(0) }
        db.readableDatabase.rawQuery("SELECT COUNT(*) FROM ${DictionaryDatabase.T_USER_WORD}", null).use { if (it.moveToFirst()) userCount = it.getInt(0) }

        layout.addView(TextView(ctx).apply {
            text = "基础词库\n单字：$charCount 个\n词组：$wordCount 条\n\n个人词库\n用户词：$userCount 条"
            textSize = 16f
        })

        // 添加词语
        val input = EditText(ctx).apply { hint = "输入要添加的词语" }
        layout.addView(input)
        layout.addView(Button(ctx).apply {
            text = "添加到个人词库"
            setOnClickListener {
                val w = input.text.toString().trim()
                if (w.isNotEmpty()) {
                    userModel.importUserWords("$w\t\t1")
                    Toast.makeText(ctx, "已添加：$w", Toast.LENGTH_SHORT).show()
                    input.setText("")
                }
            }
        })

        // 导出
        layout.addView(Button(ctx).apply {
            text = "导出个人词库"
            setOnClickListener {
                val data = userModel.exportUserWords()
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND)
                intent.type = "text/plain"
                intent.putExtra(android.content.Intent.EXTRA_TEXT, data)
                startActivity(android.content.Intent.createChooser(intent, "导出个人词库"))
            }
        })

        // 导入
        val importInput = EditText(ctx).apply { hint = "粘贴：词\\t拼音\\t频次" }
        layout.addView(importInput)
        layout.addView(Button(ctx).apply {
            text = "批量导入"
            setOnClickListener {
                val data = importInput.text.toString()
                if (data.isNotBlank()) {
                    userModel.importUserWords(data)
                    Toast.makeText(ctx, "导入完成", Toast.LENGTH_SHORT).show()
                    importInput.setText("")
                }
            }
        })

        scroll.addView(layout)
        setContentView(scroll)
    }
}
