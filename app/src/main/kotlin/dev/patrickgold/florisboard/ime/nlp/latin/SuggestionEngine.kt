package dev.patrickgold.florisboard.ime.nlp.latin

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.min

data class ScoredWord(val word: String, val score: Double)

/**
 * Pure-Kotlin English engine: prefix completion, next-word prediction and typo correction with a keyboard-aware edit
 * distance. Base words come from the bundled dictionary, personal words/bigrams from [UserDictionary].
 */
class SuggestionEngine(private val base: Map<String, Int>, private val user: UserDictionary) {
    private val sortedBase: Array<String> = base.keys.sorted().toTypedArray()
    private val byLength: Map<Int, List<String>> = base.keys.groupBy { it.length }

    fun isKnown(word: String): Boolean {
        val w = word.lowercase()
        return (w in base || user.contains(w)) && !user.isBlocked(w)
    }

    private fun userBoost(count: Int): Double =
        if (count <= 0) 0.0 else min(0.6, ln(1.0 + count) / ln(40.0) * 0.6)

    fun nextWords(previous: String?, limit: Int): List<ScoredWord> {
        val out = LinkedHashMap<String, Double>()
        if (!previous.isNullOrEmpty()) {
            for ((w, c) in user.followers(previous, limit)) out[w] = 1.0 + userBoost(c)
        }
        for ((w, c) in user.topWords(limit)) out.putIfAbsent(w, 0.5 + userBoost(c))
        return out.entries.sortedByDescending { it.value }.take(limit).map { ScoredWord(it.key, it.value) }
    }

    fun complete(prefix: String, limit: Int): List<ScoredWord> {
        val p = prefix.lowercase()
        val out = HashMap<String, Double>()
        var i = lowerBound(p)
        var seen = 0
        while (i < sortedBase.size && sortedBase[i].startsWith(p) && seen < 400) {
            val w = sortedBase[i]
            if (w != p && !user.isBlocked(w)) out[w] = (base[w] ?: 0) / 255.0
            i++; seen++
        }
        for ((w, c) in user.words()) {
            if (w.startsWith(p) && w != p) out[w] = (out[w] ?: 0.0) + 0.4 + userBoost(c)
        }
        return out.entries.sortedByDescending { it.value }.take(limit).map { ScoredWord(it.key, it.value) }
    }

    fun correct(typed: String, limit: Int): List<ScoredWord> {
        val t = typed.lowercase()
        if (t.length < 2) return emptyList()
        val maxCost = when {
            t.length <= 3 -> 1.0
            t.length <= 5 -> 1.5
            else -> 2.0
        }
        val out = HashMap<String, Double>()
        val userWords = user.words()
        fun consider(w: String, freq: Double, userCount: Int) {
            if (w == t || user.isBlocked(w)) return
            val cost = distance(t, w, maxCost)
            if (cost > maxCost) return
            val sim = 1.0 - cost / (maxCost + 0.5)
            val prefixBonus = if (w[0] == t[0]) 0.1 else 0.0
            val score = sim * 0.6 + freq * 0.3 + prefixBonus + userBoost(userCount)
            if (score > (out[w] ?: -1.0)) out[w] = score
        }
        val lo = (t.length - maxCost.toInt()).coerceAtLeast(1)
        for (len in lo..t.length + maxCost.toInt()) {
            byLength[len]?.forEach { w -> consider(w, (base[w] ?: 0) / 255.0, userWords[w] ?: 0) }
        }
        for ((w, c) in userWords) if (abs(w.length - t.length) <= maxCost.toInt()) consider(w, 0.3, c)
        return out.entries.sortedByDescending { it.value }.take(limit).map { ScoredWord(it.key, it.value) }
    }

    private fun lowerBound(p: String): Int {
        var lo = 0
        var hi = sortedBase.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (sortedBase[mid] < p) lo = mid + 1 else hi = mid
        }
        return lo
    }

    /**
     * Damerau-Levenshtein where substituting a neighboring QWERTY key costs 0.5, a transposition 1 and everything else 1.
     * Returns a value > [limit] early if the words are too far apart.
     */
    private fun distance(a: String, b: String, limit: Double): Double {
        val n = a.length
        val m = b.length
        if (abs(n - m) > limit) return limit + 1
        val d = Array(n + 1) { DoubleArray(m + 1) }
        for (i in 0..n) d[i][0] = i.toDouble()
        for (j in 0..m) d[0][j] = j.toDouble()
        for (i in 1..n) {
            var rowMin = Double.MAX_VALUE
            for (j in 1..m) {
                val sub = when {
                    a[i - 1] == b[j - 1] -> 0.0
                    KeyProximity.areNeighbors(a[i - 1], b[j - 1]) -> 0.5
                    else -> 1.0
                }
                var v = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + sub)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) v = minOf(v, d[i - 2][j - 2] + 1)
                d[i][j] = v
                if (v < rowMin) rowMin = v
            }
            if (rowMin > limit) return limit + 1
        }
        return d[n][m]
    }
}
