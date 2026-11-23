# -*- coding: utf-8 -*-
"""无忧输入法 V2.0 —— 离线词库生成器
数据源（真实开源语料）：
  1. junda_modern.txt : Jun Da 现代汉语字频表（字符、出现次数）
  2. jieba_dict.txt   : jieba 词频词典（约5万词条）
  3. pypinyin         : 真实拼音读音（词组层面消歧）
输出：
  assets/dict/chars.txt  单字库  字\t拼音\t频次排名
  assets/dict/words.txt  词组库  词\t拼音(空格分隔)\t词频
"""
import os
from pypinyin import pinyin, Style

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
ASSET = os.path.join(ROOT, "app", "src", "main", "assets", "dict")
os.makedirs(ASSET, exist_ok=True)

def load_char_freq():
    freq = {}
    with open(os.path.join(ROOT, "junda_modern.txt"), "rb") as f:
        text = f.read().decode("gb18030", errors="replace")
    for line in text.splitlines():
        if not line or not line[0].isdigit():
            continue
        parts = line.split("\t")
        if len(parts) >= 3 and len(parts[1].strip()) == 1:
            try:
                freq[parts[1].strip()] = int(parts[2])
            except ValueError:
                pass
    return freq

def load_jieba_words():
    words = []
    with open(os.path.join(ROOT, "jieba_dict.txt"), encoding="utf-8", errors="ignore") as f:
        for line in f:
            parts = line.strip().split()
            if len(parts) >= 2:
                w = parts[0]
                try:
                    cnt = int(parts[1])
                except ValueError:
                    continue
                if 2 <= len(w) <= 6 and all("\u4e00" <= c <= "\u9fff" for c in w):
                    words.append((w, cnt))
    return words

def word_pinyin(w):
    res = pinyin(w, style=Style.NORMAL, heteronym=False, errors="default")
    return [r[0] for r in res if r and r[0]]

def main():
    char_freq = load_char_freq()
    chars = []
    for ch, freq in char_freq.items():
        py = pinyin(ch, style=Style.NORMAL, heteronym=False)[0][0]
        chars.append((ch, py, freq))
    chars.sort(key=lambda x: -x[2])
    chars = [c for c in chars if c[2] >= 100]
    with open(os.path.join(ASSET, "chars.txt"), "w", encoding="utf-8") as f:
        for i, (ch, py, freq) in enumerate(chars, 1):
            f.write("%s\t%s\t%d\n" % (ch, py, i))
    print("单字库:", len(chars))

    words = load_jieba_words()
    seen = set(); out = []
    for w, cnt in words:
        if w in seen:
            continue
        syl = word_pinyin(w)
        if len(syl) != len(w) or any(not s.isascii() for s in syl):
            continue
        seen.add(w); out.append((w, " ".join(syl), cnt))
    out.sort(key=lambda x: -x[2])
    with open(os.path.join(ASSET, "words.txt"), "w", encoding="utf-8") as f:
        for w, py, cnt in out:
            f.write("%s\t%s\t%d\n" % (w, py, cnt))
    print("词组库:", len(out))
    print("校验 zhong guo ->", [w for w, py, _ in out if py == "zhong guo"][:5])
    print("校验 chang jiang ->", [w for w, py, _ in out if py == "chang jiang"][:5])

if __name__ == "__main__":
    main()
