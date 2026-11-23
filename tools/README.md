# 词库生成工具

本目录的脚本用于从第三方词表生成 `app/src/main/assets/dict/` 下的两个数据文件。
**生成结果已随仓库提交**，普通构建不需要运行这些脚本。

仅当你需要更新词表、调整词频或修正拼音标注时才使用。

## 依赖

```bash
pip install pypinyin
```

另外需要 jieba 官方词表 `dict.txt`（约 349,000 行）：

```bash
# 方式一：从 jieba 源码包取（推荐，可校验）
pip download jieba==0.42.1 --no-binary :all: -d /tmp/jieba
tar xzf /tmp/jieba/jieba-0.42.1.tar.gz -C /tmp/jieba
# 词表位置：/tmp/jieba/jieba-0.42.1/jieba/dict.txt

# 方式二：从 GitHub 仓库直接取
curl -L -o dict_full.txt \
  https://raw.githubusercontent.com/fxsjy/jieba/master/jieba/dict.txt
```

jieba 词表以 MIT 许可分发，使用时须保留其版权声明，见仓库根目录
`THIRD_PARTY_NOTICES.md`。

## 生成步骤

把 `dict.txt` 放到仓库根目录并命名为 `jieba_dict.txt`，然后执行：

```bash
# 1. 由 jieba 词表 + Jun Da 字频表生成基础词库
python3 tools/gen_dict.py

# 2. 补充高频专名与常用词（品牌、地名、口语词等）
python3 tools/gen_extra.py
```

产物：

| 文件 | 内容 |
|------|------|
| `assets/dict/chars.txt` | 单字：字 \t 拼音 \t 频率排名 |
| `assets/dict/words.txt` | 词组：词 \t 拼音（空格分隔）\t 词频 |

拼音由 pypinyin 按**词组**标注，因此多音字在词中取正确读音
（例：长江 → `chang jiang`，而非 `zhang jiang`）。

## 校验

生成后建议抽查：

```bash
grep -P "^\xe7\xbe\x8e\xe5\x9b\xbd\t" app/src/main/assets/dict/words.txt   # 美国
grep -P "^\xe9\x95\xbf\xe6\xb1\x9f\t" app/src/main/assets/dict/words.txt   # 长江
wc -l app/src/main/assets/dict/words.txt
```

更新词库后，请同时递增 `DictionaryImporter.ASSET_VERSION`，
以触发已安装用户重建数据库。
