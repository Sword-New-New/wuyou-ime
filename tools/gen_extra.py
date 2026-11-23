# -*- coding: utf-8 -*-
"""补充词库：常用口语词、专名、功能词、网络词。真实拼音由 pypinyin 生成。"""
import os
from pypinyin import pinyin, Style

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSET = os.path.join(ROOT, "app", "src", "main", "assets", "dict")

# 高频补充词（词, 频次等级）。拼音由 pypinyin 词组层面消歧生成，保证真实。
EXTRA = """中国 北京 上海 广州 深圳 天津 重庆 南京 杭州 武汉 成都 西安
长江 黄河 长城 太湖 珠江 泰山 华山
微信 QQ 支付宝 手机 电脑 平板 耳机 充电器 屏幕 键盘 鼠标
互联网 人工智能 大数据 云计算 软件 硬件 程序 应用 系统 网络
今天 明天 昨天 现在 以后 之前 最近 刚才 马上 立刻
天气 温度 早上 中午 下午 晚上 夜里 凌晨
工作 学习 生活 学校 公司 医院 商店 超市 银行 车站 机场
朋友 同学 老师 同事 家人 爸爸 妈妈 哥哥 姐姐 弟弟 妹妹
因为 所以 但是 如果 虽然 已经 正在 可以 应该 需要
觉得 知道 明白 理解 希望 喜欢 讨厌 高兴 开心 快乐 难过
手机号 电话 地址 邮箱 密码 账号 验证码 消息 文件 图片 视频
谢谢 不用 对不起 没关系 辛苦了 加油 没问题 好的 收到 明白
怎么样 怎么办 为什么 怎么 什么 哪里 哪个 谁 多少 几个
一起 马上 等等 然后 另外 此外 总之 其实 确实 一定
打卡 上班 下班 加班 出差 开会 汇报 项目 任务 截止
好吃 好看 好听 好玩 有意思 不错 漂亮 美丽 舒服 方便
下载 上传 保存 打开 关闭 删除 编辑 复制 粘贴 搜索 分享
支付宝 微博 抖音 快手 知乎 淘宝 京东 拼多多 美团 饿了么
高铁 火车 地铁 公交 出租车 网约车 飞机 轮船 自行车 电动车
医生 护士 病人 药品 治疗 检查 手术 挂号 体检
考试 作业 复习 预习 课程 教室 图书馆 宿舍 食堂 毕业
旅游 度假 出发 到达 住宿 景点 门票 行程 拍照
天气真好 心情不错 没问题 一会儿 等一下 马上到 已经到了
人工智能 无忧输入法 输入法 候选词 拼音 键盘 词库 个性化 隐私"""

def wp(w):
    r = pinyin(w, style=Style.NORMAL, heteronym=False, errors="default")
    s = [x[0] for x in r if x and x[0]]
    return s

def main():
    lines = EXTRA.strip().splitlines()
    out = []
    seen = set()
    rank = 30000  # 补充词给一个基础频次，低于核心高频词
    for block in lines:
        for w in block.split():
            if w in seen or len(w) < 2:
                continue
            syl = wp(w)
            if len(syl) != len(w) or any(not s.isascii() for s in syl):
                continue
            seen.add(w)
            out.append((w, " ".join(syl), rank))
            rank = max(1000, rank - 1)
    # 与现有 words.txt 合并去重
    path = os.path.join(ASSET, "words.txt")
    existing = {}
    with open(path, encoding="utf-8") as f:
        for line in f:
            parts = line.rstrip("\n").split("\t")
            if len(parts) >= 3:
                existing[parts[0]] = (parts[0], parts[1], int(parts[2]))
    added = 0
    for w, py, r in out:
        if w not in existing:
            existing[w] = (w, py, r)
            added += 1
    merged = sorted(existing.values(), key=lambda x: -x[2])
    with open(path, "w", encoding="utf-8") as f:
        for w, py, r in merged:
            f.write(f"{w}\t{py}\t{r}\n")
    print(f"补充词 {added} 条，合并后总词数 {len(merged)}")
    for w in ['长江','手机','明天','工作','微信','因为']:
        print(w, '->', existing[w][1] if w in existing else 'MISSING')

if __name__ == "__main__":
    main()
