package com.wuyouai.ime.prediction

import android.content.Context
import android.database.Cursor
import com.wuyouai.ime.engine.ParseScheme
import com.wuyouai.ime.engine.PinyinParser
import com.wuyouai.ime.personalization.UserModel
import com.wuyouai.ime.storage.DictionaryDatabase

/**
 * 候选词引擎。
 * 综合：拼音匹配度、词库词频、上下文（前词）、用户本地学习频率，对候选排序。
 */
data class Candidate(
    val text: String,        // 候选文本（汉字或英文）
    val pinyin: String,      // 对应拼音
    val score: Double,       // 综合得分
    val source: Int,         // 0=单字 1=词库 2=用户词 3=联想 4=纠错 5=整句
    /** 该候选用掉了输入串开头的几个字母；上屏后只删这一段，其余拼音继续留着 */
    val consumedLetters: Int = 0
)

class CandidateEngine(private val context: Context) {

    private val db = DictionaryDatabase.get(context)
    private val userModel = UserModel.get(context)


    // ---- 整句候选支持 ----

    /** 拼音键 -> (词, 该单元得分)，带内存缓存避免每键重复查库 */
    private val wordCache = java.util.concurrent.ConcurrentHashMap<String, Pair<String, Double>>()
    private val emptyMark = "" to 0.0

    /** 每切出一个分词单元扣的分：切得越碎总分越低 */
    private val SEG_PENALTY = 300.0

    /**
     * 查一个拼音键的候选词并直接给出它在整句里的得分。
     *
     * 打分规则（这套参数是拿真实词库跑验证集调出来的）：
     *  - 词：log10(词频+1)*100 + (音节数-1)*10 - 300
     *  - 单字：-120 + 40*(1 - rank/3000)，即绝对低分，只用来兜底
     *
     * 为什么要这样：如果单字也给"看起来像词频"的分，多个单字累加会压过
     * 一个整词（log 是可加的），结果整句会拼成「今天天其」「没国」这类碎切。
     * 单字必须是负分兜底，词才稳定优先。
     */
    private fun topWordFor(key: String): Pair<String, Double>? {
        wordCache[key]?.let { return if (it.first.isNotEmpty()) it else null }

        var result: Pair<String, Double>? = null

        // 1) 词组
        db.readableDatabase.rawQuery(
            "SELECT word,freq FROM ${DictionaryDatabase.T_WORD} WHERE pinyin=? ORDER BY freq DESC LIMIT 1",
            arrayOf(key)
        ).use { c ->
            if (c.moveToNext()) {
                val w = c.getString(0)
                val f = c.getDouble(1)
                val sylCount = key.count { it == ' ' } + 1
                val sc = Math.log10(f + 1.0) * 100 + (sylCount - 1) * 10 - SEG_PENALTY
                result = w to sc
            }
        }

        // 2) 单字兜底（仅当 key 是单个音节）
        if (result == null && !key.contains(" ")) {
            db.readableDatabase.rawQuery(
                "SELECT char,rank FROM ${DictionaryDatabase.T_CHAR} WHERE pinyin=? ORDER BY rank ASC LIMIT 1",
                arrayOf(key)
            ).use { c ->
                if (c.moveToNext()) {
                    val ch = c.getString(0)
                    val rank = c.getInt(1)
                    val sc = -120.0 + 40.0 * (1.0 - minOf(rank, 3000) / 3000.0)
                    result = ch to sc
                }
            }
        }

        val r = result
        if (r != null) {
            if (wordCache.size < 4000) wordCache[key] = r
            return r
        }
        if (wordCache.size < 4000) wordCache[key] = emptyMark
        return null
    }

    /**
     * 用 DP 在音节序列上做最优分词，拼出整句。
     *
     * dp[i] = 覆盖 syls[0,i) 的最优 (文本, 累计得分)
     * 最后一段最多回看 4 个音节，兼顾质量与查询量。
     */
    fun buildBestSentence(syls: List<String>): String? {
        if (syls.isEmpty()) return null
        if (syls.size == 1) return topWordFor(syls[0])?.first

        val n = syls.size
        val texts = arrayOfNulls<String>(n + 1)
        val scores = DoubleArray(n + 1) { Double.NEGATIVE_INFINITY }
        texts[0] = ""
        scores[0] = 0.0

        for (i in 1..n) {
            val maxLen = minOf(4, i)
            for (len in 1..maxLen) {
                val start = i - len
                if (texts[start] == null) continue
                val key = syls.subList(start, i).joinToString(" ")
                val unit = topWordFor(key) ?: continue
                val sc = scores[start] + unit.second
                if (sc > scores[i]) {
                    scores[i] = sc
                    texts[i] = texts[start] + unit.first
                }
            }
        }
        return texts[n]
    }

    /** 整句消耗的字母数（去掉空格后的总长度） */
    private fun lettersOf(syls: List<String>): Int = syls.sumOf { it.length }
    /** 主查询：给定输入拼音，返回排序后的候选 */
    /** 主查询：给定输入拼音串，返回排序后的候选 */
    fun query(
        input: String,
        prevWord: String? = null,
        fuzzy: Boolean = false,
        correct: Boolean = true,
        limit: Int = 20
    ): List<Candidate> {
        if (input.isBlank()) return emptyList()
        val schemes = PinyinParser.parse(input, fuzzy, correct)
        if (schemes.isEmpty()) return emptyList()

        val merged = LinkedHashMap<String, Candidate>()

        // 0) 整句候选：放在最前面，用户直接按空格就能把整串拼音上屏。
        //    用 DP 在词一级做最优组合，避免"按最高频单字拼"拼出「金天天气」这类错字。
        val firstSyls = schemes.firstOrNull()?.syllables ?: emptyList()
        if (firstSyls.size >= 2) {
            val sentence = try { buildBestSentence(firstSyls) } catch (t: Throwable) { null }
            if (!sentence.isNullOrBlank()) {
                merged[sentence] = Candidate(
                    text = sentence,
                    pinyin = firstSyls.joinToString(" "),
                    score = 1e6,          // 置顶
                    source = 5,
                    consumedLetters = lettersOf(firstSyls)
                )
            }
        }

        var priority = 0
        for (scheme in schemes) {
            val syls = scheme.syllables
            if (syls.isEmpty()) continue

            // 1) 整串当一个词查（能命中就直接命中，最理想）
            val whole = syls.joinToString(" ")
            collectWhole(whole, prevWord, priority, merged, limit)

            // 2) 长输入的核心：按音节前缀逐段组词
            //    用户打 jintiantianqi，应当优先出「今天天气」这种多词组合，
            //    而不是只拿第一个音节去查单字、然后把后面全丢掉。
            if (syls.size >= 2) {
                collectComposed(syls, prevWord, priority, merged, limit)
            }

            // 3) 首音节单字兜底（保证任何输入至少有字可选）
            collectChars(syls.first(), prevWord, priority, merged, limit)

            // 4) 简拼/首字母
            if (scheme.isSimplified) {
                collectSimplified(syls.joinToString(""), prevWord, priority, merged, limit)
            }

            priority++
            if (merged.size >= limit * 6) break
        }

        userModel.boostUserWords(input, prevWord, merged)

        // 关键校验：只有「切分能原样拼回输入串」时，才允许选词后只删一段拼音。
        // 模糊音（zh->z）与纠错（补字）会改变音节长度，字母数对不上，
        // 此时强行按长度删就会删错位置 —— 一律退化为「上屏即清空整串」。
        val plain = input.lowercase().filter { it in 'a'..'z' }
        val rebuilt = firstSyls.joinToString("")
        val canPartial = rebuilt == plain

        val list = merged.values
            .map { c ->
                if (canPartial) c
                else c.copy(consumedLetters = plain.length)
            }
            .sortedByDescending { it.score }
            .take(limit)
            .toMutableList()

        if (list.isNotEmpty() && list[0].source != 2) {
            val assoc = userModel.associate(list[0].text)
            if (assoc.isNotEmpty()) {
                list.addAll(assoc.map {
                    Candidate(it, "", -1e9, 3, plain.length)
                })
            }
        }
        return list.distinctBy { it.text }.take(limit)
    }

    /**
     * 组合查询：把音节序列拆成「前缀词组 + 剩余单字」，优先输出最长的词组。
     *
     * 例 jintiantianqi：
     *   [今天] + 天+气        -> 今天天气
     *   [今天] + 天气        -> 今天天气
     *   [金天] + ...
     *
     * 做法是贪心取最长可匹配前缀，最多尝试前 4 个音节，避免查询次数爆炸。
     */
    /** 整串精确等于一个词条时，优先取词库里的词 */
    private fun collectWhole(
        pinyinKey: String,
        prevWord: String?,
        priority: Int,
        merged: LinkedHashMap<String, Candidate>,
        limit: Int
    ) {
        // 整串匹配的词，消耗掉全部字母
        val letters = pinyinKey.replace(" ", "").length
        db.readableDatabase.rawQuery(
            "SELECT word,freq FROM ${DictionaryDatabase.T_WORD} WHERE pinyin = ? ORDER BY freq DESC LIMIT ?",
            arrayOf(pinyinKey, limit.toString())
        ).use { c ->
            while (c.moveToNext()) {
                val w = c.getString(0)
                if (merged.containsKey(w)) continue
                val f = c.getDouble(1)
                merged[w] = Candidate(
                    w, pinyinKey,
                    kotlin.math.log10(f + 1.0) * 100.0 - priority * 2000.0,
                    1, letters
                )
            }
        }
    }

    private fun collectComposed(
        syls: List<String>,
        prevWord: String?,
        priority: Int,
        merged: LinkedHashMap<String, Candidate>,
        limit: Int
    ) {
        val joined = syls.joinToString(" ")
        val words = queryPrefixWords(joined, limit)

        for (w in words) {
            val consumed = w.second
            if (consumed >= syls.size) {
                // 「前缀」本身就是整串，交给 collectWhole 处理
                continue
            }
            // 前缀词组消耗的字母数
            val prefixLetters = syls.subList(0, consumed).sumOf { it.length }

            // a) 前缀词本身也要作为候选：用户可能只想先上屏「今天」，
            //    剩下的 tianqi 继续留着出「天气」。不输出它的话就选不到。
            if (!merged.containsKey(w.first)) {
                merged[w.first] = Candidate(
                    w.first, joined,
                    -priority * 2000.0 + w.third, 1, prefixLetters
                )
            }

            // b) 词组 + 剩余首字，组成「词组 + 下一个字」的候选
            val nextSyl = syls[consumed]
            val nextChars = queryCharsRaw(nextSyl, 3)
            for (ch in nextChars) {
                val text = w.first + ch
                val score = -priority * 2000.0 + w.third - consumed * 2.0
                if (!merged.containsKey(text)) {
                    merged[text] = Candidate(text, joined, score, 1, prefixLetters + nextSyl.length)
                }
            }
        }
    }

    /**
     * 查「整串拼音的前缀」能匹配到什么词组。
     * 返回 (词, 消耗的音节数, 词频)，按消耗音节数降序、词频降序。
     */
    private fun queryPrefixWords(joined: String, limit: Int): List<Triple<String, Int, Double>> {
        val out = ArrayList<Triple<String, Int, Double>>()
        val parts = joined.split(" ")
        // 只查前 4 个音节能组成的词组，兼顾召回与速度
        val maxN = minOf(4, parts.size)
        for (n in maxN downTo 2) {
            val key = parts.subList(0, n).joinToString(" ")
            db.readableDatabase.rawQuery(
                "SELECT word,freq FROM ${DictionaryDatabase.T_WORD} " +
                    "WHERE pinyin = ? ORDER BY freq DESC LIMIT ?",
                arrayOf(key, (limit / 2).toString())
            ).use { c ->
                while (c.moveToNext()) {
                    out.add(Triple(c.getString(0), n, c.getDouble(1)))
                }
            }
        }
        // 消耗音节多的排前面（更长的词更可能是用户的真实意图）
        out.sortWith(compareByDescending<Triple<String, Int, Double>> { it.second }
            .thenByDescending { it.third })
        return out
    }

    /** 取某音节的候选单字，用于词组拼接 */
    private fun queryCharsRaw(pinyin: String, limit: Int): List<String> {
        val out = ArrayList<String>(limit)
        db.readableDatabase.rawQuery(
            "SELECT char FROM ${DictionaryDatabase.T_CHAR} WHERE pinyin = ? ORDER BY rank ASC LIMIT ?",
            arrayOf(pinyin, limit.toString())
        ).use { c ->
            while (c.moveToNext()) out.add(c.getString(0))
        }
        return out
    }


    /** 简拼查询：拼音首字母序列匹配 */
    private fun collectSimplified(
        initials: String, prevWord: String?, priority: Int,
        merged: LinkedHashMap<String, Candidate>, limit: Int
    ) {
        if (initials.length < 2) return
        val pat = buildLike(initials)
        db.readableDatabase.rawQuery(
            """SELECT word,pinyin,freq FROM ${DictionaryDatabase.T_WORD}
               WHERE replace(pinyin,' ','') LIKE ?
               ORDER BY freq DESC LIMIT ?""",
            arrayOf("$pat%", limit.toString())
        ).use { c ->
            while (c.moveToNext()) addCandidate(c, prevWord, priority, 1, merged, simplified = true)
        }
    }



    private fun addCandidate(
        c: Cursor, prevWord: String?, priority: Int, source: Int,
        merged: LinkedHashMap<String, Candidate>, simplified: Boolean
    ) {
        val text = c.getString(0)
        val py = c.getString(1)
        val freq = c.getDouble(2)
        if (merged.containsKey(text)) return
        val freqScore = Math.log10(freq + 1) * 100
        val simplifiedPenalty = if (simplified) 800.0 else 0.0
        val ctx = contextBoost(text, prevWord)
        val userBoost = userModel.userScore(text)
        val score = freqScore + ctx + userBoost - priority * 2000 - simplifiedPenalty
        merged[text] = Candidate(text, py, score, source)
    }

    /** 上下文加分：前词与候选的常用搭配 */
    private fun contextBoost(candidate: String, prevWord: String?): Double {
        if (prevWord.isNullOrBlank()) return 0.0
        return userModel.contextScore(prevWord, candidate)
    }

    private fun buildLike(initials: String): String {
        // "zg" -> "z% g%" 形式用于首字母匹配
        return initials.split(" ").joinToString(" ") { "${it.first()}%" }
    }

    /** 单字兜底：某音节的候选单字，带上下文与用户词加权 */
    private fun collectChars(
        pinyin: String, prevWord: String?,
        priority: Int,
        merged: LinkedHashMap<String, Candidate>,
        limit: Int
    ) {
        db.readableDatabase.rawQuery(
            "SELECT char,rank FROM ${DictionaryDatabase.T_CHAR} WHERE pinyin=? ORDER BY rank ASC LIMIT ?",
            arrayOf(pinyin, limit.toString())
        ).use { c ->
            while (c.moveToNext()) {
                val text = c.getString(0)
                if (merged.containsKey(text)) continue
                val rank = c.getInt(1)
                val base = 1000.0 / (rank + 1)
                val score = base + userModel.userScore(text) - priority * 20.0
                merged[text] = Candidate(text, pinyin, score, 0, pinyin.length)
            }
        }
    }

    fun charsForPinyin(pinyin: String, limit: Int = 30): List<String> {
        val out = mutableListOf<String>()
        db.readableDatabase.rawQuery(
            "SELECT char FROM ${DictionaryDatabase.T_CHAR} WHERE pinyin=? ORDER BY rank LIMIT ?",
            arrayOf(pinyin, limit.toString())
        ).use { c -> while (c.moveToNext()) out.add(c.getString(0)) }
        return out
    }

    /** 同义词/相关词推荐（基于词库同拼音 + 用户词典） */
    fun synonyms(word: String): List<String> {
        return userModel.synonymsFor(word)
    }
}
