# 贡献指南

感谢你愿意为无忧输入法做点什么。

## 核心约束（不可协商）

这个项目的全部价值建立在「不联网」之上。以下几条是硬性红线，任何 PR 只要触及都会被拒绝：

1. **不新增网络权限** —— `INTERNET`、`ACCESS_NETWORK_STATE`、`ACCESS_WIFI_STATE` 一律不合并
2. **不引入统计/广告/推送/远程日志 SDK** —— 包括崩溃上报平台（Firebase、Bugly、Sentry 等）
    原因：它们默认都要联网，且常收集设备标识
3. **不使用 WebView 加载远程内容**
4. **不新增后台常驻服务** —— 输入法只在键盘可见时需要活着
5. **不把输入内容写入日志** —— `Log.d` 里不要出现用户输入的字符串

如果你认为某条限制阻挡了必要的功能，请先开 Issue 讨论，不要直接提 PR。

## 开发环境

- JDK 17
- Android SDK Platform 34 + Build-Tools 34.0.0
- Android Studio（推荐）或命令行 Gradle

`local.properties`、签名文件、个人构建配置请勿提交（`.gitignore` 已覆盖）。

## 提交流程

```bash
# 1. fork 并克隆
git clone https://github.com/<your-name>/wuyou-ime.git
cd wuyou-ime

# 2. 建分支（用有意义的名字）
git checkout -b fix/candidate-order

# 3. 改动 + 本地构建验证
./gradlew :app:assembleDebug

# 4. 提交，说明「改了什么 / 为什么改」
git commit -m "fix: 九宫格候选排序在长按后错乱"

# 5. 推送并开 PR

git push origin fix/candidate-order
```

## 代码风格

- Kotlin 官方风格（4 空格缩进）
- 公开函数与类加简短中文 KDoc，说明「为什么」而不是复述「做了什么」
- 输入法路径上的代码请考虑性能：`onDraw`、按键回调、候选查询不应分配大对象
- 新增设置项必须同步：`SettingsManager` 常量 + `cached*` 缓存 + `activity_settings.xml` 开关

## 修改隐私相关逻辑时

若你的改动影响数据收集、存储或学习行为，请**同时**更新：

1. `app/src/main/java/com/wuyou/ime/privacy/PrivacyActivity.kt` 中的说明文本
2. `README.md` 的「隐私设计」一节

隐私说明与实际行为不一致，是这个项目最严重的缺陷。

## 报告问题

请尽量提供：安卓版本、机型/ROM、复现步骤、预期结果、实际结果。
如果涉及输入异常，附上「输入了什么拼音」和「期望出的候选」非常有用——
词库覆盖问题可以很快定位。

## 提交 PR

- 一个 PR 只做一件事，便于审查
- 涉及界面改动的，附截图或录屏
- 涉及词库的，说明数据来源与许可
