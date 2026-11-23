# 第三方组件与数据声明

无忧输入法（Wuyou IME）在构建过程中使用或分发了下列第三方作品。
这些作品各自保留其原有版权与许可，本项目的许可证（见 LICENSE）不覆盖它们。

---

## 1. 词库数据（随 APK 分发）

### jieba 中文分词词典 dict.txt
- **用途**：中文词组与词频数据。本项目将其作为基础词表，生成
  `app/src/main/assets/dict/words.txt`（词条 + 拼音 + 词频）。
- **来源**：https://github.com/fxsjy/jieba
- **许可**：MIT License
- **版权**：Copyright (c) 2013 Sun Junyi
- **说明**：文件 `jieba/dict.txt` 是 jieba 官方发布包的一部分，随本项目
  按 MIT 条款原样使用。本项目**未**修改其条款，MIT 全文见下。

```
The MIT License (MIT)

Copyright (c) 2013 Sun Junyi

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

### Jun Da 汉字频率字表
- **用途**：单字频率排序，生成 `app/src/main/assets/dict/chars.txt` 的 rank 字段。
- **来源**：Jun Da, *Modern Chinese Character Frequency List*
  （http://lingua.mtsu.edu/chinese-computing/）
- **性质**：学术统计性质的字频数据（对汉语语料中汉字出现次数的事实统计）。
  字频本身不构成著作权保护对象，此处致谢数据整理者。
- **建议**：若你对此数据的分发有疑虑，可只保留 `words.txt` 并在生成脚本中
  改为按词频导出单字，功能不受实质影响。

### pypinyin（仅构建期使用，不随 APK 分发）
- **用途**：在线生成脚本 `tools/gen_dict.py`、`tools/gen_extra.py`
  为词条标注真实拼音读音（词组层面消歧）。
- **许可**：MIT License
- **版权**：Copyright (c) 2011-2024 mozillazg
- **说明**：属于构建期工具依赖，未被打包进 APK，也未复制其源码进入本仓库。

---

## 2. 运行时依赖（AndroidX / Kotlin，均随 APK 打包）

以下库均以 Apache License 2.0 授权，通过 Gradle 依赖引入，未修改其源码：

| 组件 | 用途 | 许可 |
|------|------|------|
| androidx.core:core-ktx | Kotlin 核心扩展 | Apache-2.0 |
| androidx.appcompat:appcompat | 兼容 Activity | Apache-2.0 |
| com.google.android.material:material | Material 3 组件与主题 | Apache-2.0 |
| androidx.datastore:datastore-preferences | 本地设置存储 | Apache-2.0 |
| org.jetbrains.kotlin:kotlin-stdlib | Kotlin 标准库 | Apache-2.0 |

AndroidX 各组件附带其自身的 `LICENSE.txt`，位于 AAR 内部；
以依赖形式引入时无需在本仓库重复分发许可全文，但再分发 APK 时
应在应用内保留相应声明。Apache-2.0 全文见
https://www.apache.org/licenses/LICENSE-2.0

---

## 3. 未使用的第三方资产

本项目**不包含**任何第三方输入法的代码、图标、字体、配色稿或词库私有数据。
界面布局遵循中文输入法的通用公开惯例（候选栏在上、字母键区居中、
功能行在下），未复制任何特定产品的素材。

## 4. 构建环境说明

本项目在 Android Studio / Gradle 官方工具链下构建。
开发过程中若使用脚本化辅助工具（例如在非 x86 宿主机上模拟 aapt2），
相关配置**仅存在于开发者本地环境**，不属于本仓库内容，也不影响源码可构建性。
请以官方 Gradle 与 Android SDK Build-Tools 构建本项目。
