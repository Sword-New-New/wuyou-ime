package com.wuyouai.ime.engine

/**
 * 拼音解析器：把用户连续敲入的字母串切成音节序列。
 *
 * 核心是一张 DP 图，而不是「枚举前 N 种方案」：
 *   dp[i] = 覆盖 s[0, i) 的所有切分方式（按代价升序，带数量上限）
 *
 * 这样保证：只要整串存在合法切分，就一定能在 dp[n] 拿到**完整覆盖**的方案，
 * 绝不会因为凑满 N 种方案就停止、把后面大半截输入丢掉。
 *
 * 代价模型（越小越可信）：
 *   - 完整音节      0      zhuang / guo / shi
 *   - 每多切一刀    +1     倾向少切，减少无谓碎片
 *   - 单字母简拼    +6     j / t 这类，全拼合法性极低
 *   - 模糊音替换    +2
 *   - 尾部截断      +3     用户还在打字，最后一个音节可能没敲完
 */
object PinyinParser {

    /** 单条输入最多保留多少种切分方案 */
    private const val MAX_SCHEMES = 24

    /** 每个位置最多保留多少种前缀状态，防止组合爆炸 */
    private const val MAX_STATES_PER_POS = 48

    /** 单个音节最长字母数（zhuang / chuang / shuang） */
    private const val MAX_SYL = 6

    /** 模糊音映射（双向） */
    private val FUZZY_PAIRS = listOf(
        "zh" to "z", "ch" to "c", "sh" to "s",
        "n" to "l", "l" to "n",
        "ang" to "an", "an" to "ang",
        "eng" to "en", "en" to "eng",
        "ing" to "in", "in" to "ing"
    )

    private val fuzzyMap: Map<String, Set<String>> = buildMap {
        FUZZY_PAIRS.forEach { (a, b) ->
            merge(a, setOf(b)) { o, n -> o + n }
            merge(b, setOf(a)) { o, n -> o + n }
        }
    }

    /** 九宫格：数字键到字母组 */
    val T9_MAP: Map<Char, String> = mapOf(
        '2' to "abc", '3' to "def", '4' to "ghi", '5' to "jkl",
        '6' to "mno", '7' to "pqrs", '8' to "tuv", '9' to "wxyz"
    )

    /**
     * 解析主入口。
     *
     * @param input   用户输入的连续小写拼音串
     * @param fuzzy   是否启用模糊音（额外产出替换声母/韵母后的方案）
     * @param correct 是否容忍末位残缺音节（用户还在打字时最后一个音节可能没敲完）
     */
    fun parse(input: String, fuzzy: Boolean = false, correct: Boolean = true): List<ParseScheme> {
        if (input.isEmpty()) return emptyList()
        val s = input.lowercase().filter { it in 'a'..'z' }
        if (s.isEmpty()) return emptyList()


        // 全拼：DP 求完整覆盖的切分
        val full = segmentFull(s, correct)

        val out = ArrayList<ParseScheme>(full.size + 4)
        full.forEach { path ->
            val syls = path.first
            out += ParseScheme(
                syllables = syls,
                isSimplified = syls.any { it.length == 1 },
                cost = path.second
            )
        }

        // 整串首字母方案（用户只打了声母）
        if (s.length >= 2) {
            out += ParseScheme(
                syllables = s.map { it.toString() },
                isSimplified = true,
                cost = 40
            )
        }

        // 模糊音扩展
        if (fuzzy) {
            out.take(6).toList().forEach { sch ->
                fuzzyVariants(sch).forEach { out += it }
            }
        }

        return out
            .distinctBy { it.syllables.joinToString("|") }
            .sortedBy { it.cost }
            .take(MAX_SCHEMES)
    }

    /**
     * 动态规划求「完整覆盖整串」的切分方案。
     *
     * 返回若干 (音节列表, 代价)，按代价升序。列表里的方案拼起来**必等于原串**，
     * 不存在只覆盖前缀的残片 —— 这正是长拼音只出一个字的病根。
     */
    private fun segmentFull(
        s: String,
        allowPartialTail: Boolean
    ): List<Pair<List<String>, Int>> {
        val n = s.length
        val best = arrayOfNulls<MutableList<Pair<List<String>, Int>>>(n + 1)
        best[0] = mutableListOf<Pair<List<String>, Int>>(emptyList<String>() to 0)

        for (i in 1..n) {
            val pool = ArrayList<Pair<List<String>, Int>>()
            val maxLen = minOf(MAX_SYL, i)
            for (len in 1..maxLen) {
                val start = i - len
                val piece = s.substring(start, i)
                val prevList = best[start]
                if (prevList == null) continue
                if (prevList.isEmpty()) continue

                val isSingle = len == 1
                if (isSingle) {
                    for (prev in prevList) {
                        val list = prev.first + piece
                        pool += list to (prev.second + 6)
                    }
                    continue
                }

                if (!PinyinSyllables.isSyllable(piece)) continue
                for (prev in prevList) {
                    val list = prev.first + piece
                    pool += list to (prev.second + 1)
                }
            }

            if (allowPartialTail && i == n && n >= 2) {
                for (len in 2..minOf(MAX_SYL, n)) {
                    val piece = s.substring(n - len, n)
                    if (PinyinSyllables.isSyllable(piece)) continue
                    if (!PinyinSyllables.SET.any { it.startsWith(piece) }) continue
                    val prevList = best[n - len]
                    if (prevList == null) continue
                    if (prevList.isEmpty()) continue
                    for (prev in prevList) {
                        val list = prev.first + piece
                        pool += list to (prev.second + 3)
                    }
                }
            }

            // 按代价排序并截断：dp 状态数控制在 O(n * STATES)，
            // 否则 41 个字母的输入会组合爆炸（这正是之前卡顿的来源之一）
            pool.sortBy { it.second }
            best[i] = if (pool.size > MAX_STATES_PER_POS) {
                ArrayList(pool.subList(0, MAX_STATES_PER_POS))
            } else {
                pool
            }
        }

        return best[n] ?: emptyList()
    }

    private fun fuzzyVariants(sch: ParseScheme): List<ParseScheme> {
        val out = ArrayList<ParseScheme>()
        sch.syllables.forEachIndexed { idx, syl ->
            val alts = fuzzyMap[syl]
            if (alts != null) {
                for (alt in alts) {
                    if (alt.isEmpty()) continue
                    val v = ArrayList(sch.syllables)
                    v[idx] = alt
                    out += ParseScheme(v, sch.isSimplified, sch.cost + 2)
                }
            }
        }
        return out
    }

    fun t9ToPinyinCandidates(digits: String, limit: Int = 200): List<String> {
        if (digits.isEmpty()) return emptyList()
        if (digits.any { it !in '2'..'9' }) return emptyList()

        var acc = listOf("")
        for (d in digits) {
            val letters = T9_MAP[d]
            if (letters == null) return emptyList()
            val next = ArrayList<String>(acc.size * letters.length)
            for (base in acc) {
                for (ch in letters) {
                    val cand = base + ch
                    val isPrefix = PinyinSyllables.SET.any { it.startsWith(cand) }
                    if (isPrefix) {
                        next.add(cand)
                    }
                    if (next.size >= limit) break
                }
                if (next.size >= limit) break
            }
            acc = next
            if (acc.isEmpty()) return emptyList()
        }
        return acc
    }
}

/** 一种切分方案：音节序列 + 是否含简拼 + 代价（越小越可信） */
data class ParseScheme(
    val syllables: List<String>,
    val isSimplified: Boolean,
    val cost: Int = 0
)
