# Wuyou IME V2.0

> Smart doesn't need the cloud. Privacy doesn't need compromise.

A fully offline, native Android Pinyin input method with **zero network permission**.
The dictionary ships inside the APK; learning happens only in memory and in the app's
private database. Airplane mode changes nothing.

[简体中文](README.md) | **English**

---

## What makes it different

Most IMEs can't work without a network, because prediction, correction and dictionary
updates live in the cloud. Wuyou IME moves all of that onto the device:

| Capability | Typical IME | Wuyou IME |
|-----------|-------------|-----------|
| Dictionary | Downloaded from cloud | **336,900 words built into the APK** |
| Long-sentence prediction | Server-side inference | Local DP segmentation + frequency scoring |
| Smart correction | Cloud model | Local truncation completion + fuzzy pinyin |
| Personalization | Uploaded profile | **Written only to a private on-device SQLite DB** |
| Privacy promise | Policy text | **No network capability at the OS level** |

Verify it yourself: *Settings → Apps → Wuyou IME → Permissions*. There is no network
permission. Turn on airplane mode and you can still type every sentence.

## Features

- **Pinyin input**: full pinyin / abbreviated / initials; fuzzy pinyin (zh-z, n-l, an-ang…);
  smart correction tolerating dropped letters
- **Long sentences**: dynamic programming over all valid syllable splits, ranked by
  word frequency plus context
- **Polyphone disambiguation**: readings annotated per word, so 长江 is `chang jiang`,
  never `zhang`
- **Layouts**: QWERTY (Pinyin / English), 9-key T9, symbols, numbers, emoji
- **English**: prediction, correction, auto-capitalization, auto-space
- **Local learning**: word and context frequency; **no learning at all** in password
  fields or privacy mode
- **Clipboard manager**: local history, **off by default**, read only while the keyboard
  is in the foreground
- **Smart phrases**: built-in set plus user-defined abbreviation expansion
- **8 themes**, including two **Liquid Glass** styles (translucent caps, top specular
  highlight, bottom refraction arc)

## Privacy design

Not a slogan — a few checkable technical decisions:

1. **No `INTERNET` permission declared.**
   It is absent from `AndroidManifest.xml`. Under Android's permission model the app
   cannot make any network request, regardless of what the code says.

2. **No third-party network SDKs.**
   No analytics, ads, push, crash reporting or remote logging. Usage statistics have
   nowhere to go, because there is nowhere to send them.

3. **Input never leaves the process.**
   Password fields, visible-password fields and editors flagged
   `IME_FLAG_NO_PERSONALIZED_LEARNING` are never learned, recorded or used for suggestion.

4. **Learning data stays in the app sandbox.**
   User words and context pairs live in a private SQLite database other apps cannot read.

5. **Cloud backup and device transfer are disabled.**
   `allowBackup=false` plus `data_extraction_rules.xml`, which explicitly excludes all four
   domains: `root`, `database`, `sharedpref`, `file`. Your dictionary is never synced to
   any account.

6. **Clipboard is not read by default.**
   History is off until you enable it, and reads only happen while the keyboard is visible
   and you reach for it.

## Build

Requirements: JDK 17, Android SDK (Platform 34 + Build-Tools 34.0.0)

```bash
git clone https://github.com/<your-org>/wuyou-ime.git
cd wuyou-ime
./gradlew :app:assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

Install and enable:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
# Settings → System → Languages & input → On-screen keyboard → enable "Wuyou IME"
# Settings → Languages & input → Current keyboard → choose "Wuyou IME"
```

Or open the app and follow its two-step setup guide.

## Dictionary

Two tab-separated text files under `app/src/main/assets/dict/`:

| File | Rows | Format |
|------|------|--------|
| `chars.txt` | 6,663 | `char<TAB>pinyin<TAB>rank` |
| `words.txt` | 336,900 | `word<TAB>pinyin (space separated)<TAB>frequency` |

`DictionaryImporter` loads them into SQLite on first launch in batches of 20,000 per
transaction. Bump `ASSET_VERSION` to force a re-import.

Generated from the jieba word list (MIT); pinyin annotated with pypinyin.
See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Project layout

```
app/src/main/java/com/wuyou/ime/
├── WuyouApplication.kt          Entry point: settings cache, background dictionary import
├── keyboard/
│   ├── WuyouInputMethodService.kt   The InputMethodService (core)
│   ├── KeyboardView.kt              Painted keyboard: QWERTY / T9 / symbols / numbers
│   ├── CandidateBarView.kt          Candidate strip
│   └── FeedbackHelper.kt            Haptics and key clicks
├── engine/
│   ├── PinyinSyllables.kt           Valid syllable table
│   └── PinyinParser.kt              DP segmentation, fuzzy pinyin, correction, T9
├── prediction/CandidateEngine.kt    Ranking: frequency + context + user words
├── personalization/
│   ├── UserModel.kt                 Local learning
│   └── SmartPhrase.kt               Smart phrases
├── dictionary/DictionaryImporter.kt Built-in dictionary import
├── storage/DictionaryDatabase.kt    SQLite schema
├── settings/                        SettingsManager / settings screens
├── themes/ThemeManager.kt           Theme definitions
├── emoji/EmojiData.kt               Emoji categories
├── clipboard/ClipboardManager.kt    Clipboard history
└── privacy/PrivacyActivity.kt       Privacy disclosure
```

## Tech stack

- Kotlin, `InputMethodService` — a real system IME, not an in-app virtual keyboard
- minSdk 26 (Android 8.0) / targetSdk & compileSdk 34
- SQLite via `SQLiteOpenHelper` (no ORM), DataStore Preferences
- Material 3
- Keyboard caps and candidate strip are painted on `Canvas`; no third-party UI widgets

## License

Source code is licensed under the **Mozilla Public License 2.0** — see [LICENSE](LICENSE).

MPL-2.0 rather than MIT/Apache was chosen deliberately: it carries **file-level copyleft**.
You may use this project in closed-source products and you may modify it, but the files you
modify must remain open under MPL. That keeps commercial integration easy while preventing
the project from being silently privatized — a reasonable constraint for software that
handles everything a user types.

Third-party licenses for dictionary data are in
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md); the jieba word list is MIT and requires
preserving its copyright notice.

## Contributing

Issues and pull requests are welcome. Before submitting, please confirm:

- No network dependencies, analytics SDKs or tracking beacons are introduced
- No network permissions (e.g. `INTERNET`) are added
- Any change in data handling is mirrored in `PrivacyActivity`
- Offline behavior is preserved: nothing should degrade in airplane mode

## Disclaimer

An independent implementation. Not affiliated with any input method vendor. Contains no
code, icons, fonts or proprietary dictionaries from third-party keyboards.
