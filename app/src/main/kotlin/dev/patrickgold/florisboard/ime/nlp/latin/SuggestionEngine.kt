package dev.patrickgold.florisboard.ime.nlp.latin

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.min

data class ScoredWord(val word: String, val score: Double)

/**
 * Pure-Kotlin English engine: prefix completion, next-word prediction (using the last one, two or three words as
 * context, with back-off) and typo correction with a keyboard- and touch-aware edit distance (optimal string
 * alignment: insertions, deletions, substitutions and adjacent transpositions). Base words come from the
 * bundled dictionary, personal words and word sequences from [UserDictionary].
 */
class SuggestionEngine(private val base: Map<String, Int>, private val user: UserDictionary) {
    private val sortedBase: Array<String> = base.keys.sorted().toTypedArray()
    // Most frequent words first, so the per-bucket scan budget in correct() keeps the likeliest candidates.
    private val byLength: Map<Int, List<String>> = base.keys.sortedByDescending { base[it] ?: 0 }.groupBy { it.length }

    /**
     * Immutable snapshot of the personal words, sorted for prefix lookups. The touch path only ever reads the current
     * snapshot (bounded binary search + short scan); rebuilding happens on the background suggestion path.
     */
    private class UserIndex(val words: Array<String>, val counts: IntArray, val map: Map<String, Int>, val version: Int)
    @Volatile private var userIndex = UserIndex(emptyArray(), IntArray(0), emptyMap(), -1)

    private fun refreshUserIndex() {
        val v = user.version
        if (v == userIndex.version) return
        val map = HashMap<String, Int>()
        user.forEachWord { w, c -> map[w] = c }
        val sorted = map.keys.sorted().toTypedArray()
        userIndex = UserIndex(sorted, IntArray(sorted.size) { map[sorted[it]] ?: 0 }, map, v)
    }

    private fun userLowerBound(idx: UserIndex, p: String): Int {
        var lo = 0
        var hi = idx.words.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (idx.words[mid] < p) lo = mid + 1 else hi = mid
        }
        return lo
    }

    /** Prefix sums of word weights over [sortedBase] so the weight of every word starting with some prefix is O(log n). */
    private val prefixWeight: DoubleArray = DoubleArray(sortedBase.size + 1).also { sums ->
        for (i in sortedBase.indices) sums[i + 1] = sums[i] + weightOf(base[sortedBase[i]] ?: 0)
    }

    private val topBase: List<ScoredWord> = base.entries
        .filter { it.key.length >= 2 || it.key == "i" || it.key == "a" }
        .sortedByDescending { it.value }.take(40).map { ScoredWord(it.key, it.value / 255.0) }

    private fun weightOf(freq: Int): Double {
        val f = freq / 255.0
        return f * f + 0.001
    }

    fun isKnown(word: String): Boolean {
        val w = word.lowercase()
        return (w in base || user.contains(w)) && !user.isBlocked(w)
    }

    private fun userBoost(count: Int): Double =
        if (count <= 0) 0.0 else min(0.6, ln(1.0 + count) / ln(40.0) * 0.6)

    /**
     * How strongly each word is expected after [context] (oldest word first, at most three). Longer matching contexts
     * count more; shorter ones are used as back-off.
     */
    private fun contextScores(context: List<String>): Map<String, Double> {
        if (context.isEmpty()) return emptyMap()
        val ctx = context.takeLast(UserDictionary.MAX_CONTEXT).map { it.lowercase() }
        val out = HashMap<String, Double>()
        for (k in ctx.size downTo 1) {
            val followers = user.followers(ctx.takeLast(k))
            if (followers.isEmpty()) continue
            val total = followers.values.sum().toDouble()
            // A context seen only once is weak evidence, one seen many times is strong.
            val confidence = min(1.0, 0.4 + ln(1.0 + total) / ln(8.0) * 0.6)
            val orderWeight = when (k) { 3 -> 1.0; 2 -> 0.65; else -> 0.35 }
            for ((w, c) in followers) {
                out[w] = (out[w] ?: 0.0) + orderWeight * confidence * (c / total)
            }
        }
        return out
    }

    fun nextWords(context: List<String>, limit: Int): List<ScoredWord> {
        refreshUserIndex()
        val out = LinkedHashMap<String, Double>()
        for ((w, s) in contextScores(context)) if (!user.isBlocked(w)) out[w] = 1.0 + s
        for ((w, c) in user.topWords(limit)) out.putIfAbsent(w, 0.5 + userBoost(c))
        for (sw in topBase) if (!user.isBlocked(sw.word)) out.putIfAbsent(sw.word, 0.2 + sw.score * 0.1)
        return out.entries.sortedByDescending { it.value }.take(limit).map { ScoredWord(it.key, it.value) }
    }

    fun complete(prefix: String, context: List<String>, limit: Int): List<ScoredWord> {
        refreshUserIndex()
        val p = prefix.lowercase()
        val ctxScores = contextScores(context)
        val out = HashMap<String, Double>()
        var i = lowerBound(p)
        var seen = 0
        while (i < sortedBase.size && sortedBase[i].startsWith(p) && seen < 8000) {
            val w = sortedBase[i]
            if (w != p && !user.isBlocked(w)) {
                out[w] = (base[w] ?: 0) / 255.0 - 0.002 * (w.length - p.length) + (ctxScores[w] ?: 0.0) * 0.8
            }
            i++; seen++
        }
        val ui = userIndex
        var u = userLowerBound(ui, p)
        var userSeen = 0
        while (u < ui.words.size && ui.words[u].startsWith(p) && userSeen < 2000) {
            val w = ui.words[u]
            if (w.length > p.length) {
                out[w] = (out[w] ?: ((ctxScores[w] ?: 0.0) * 0.8)) + 0.4 + userBoost(ui.counts[u])
            }
            u++; userSeen++
        }
        for ((w, s) in ctxScores) {
            if (w.length > p.length && w.startsWith(p) && w !in out && !user.isBlocked(w)) out[w] = 0.4 + s * 0.8
        }
        return out.entries.sortedByDescending { it.value }.take(limit).map { ScoredWord(it.key, it.value) }
    }

    /**
     * Corrections for [typed]. [hints] holds, for every typed letter, the second most likely key given where the finger
     * actually landed (or null). Swapping a letter for its hint is almost free, because the finger was nearly there.
     */
    fun correct(typed: String, context: List<String>, limit: Int, hints: List<Char?>? = null): List<ScoredWord> {
        refreshUserIndex()
        val t = typed.lowercase()
        if (t.length < 2) return emptyList()
        val maxCost = when {
            t.length <= 3 -> 1.0
            t.length <= 5 -> 1.5
            else -> 2.0
        }
        val ctxScores = contextScores(context)
        val out = HashMap<String, Double>()
        val userWords = userIndex.map
        // One DP matrix reused for every candidate instead of allocating a new one each time.
        val matrix = Array(t.length + 1) { DoubleArray(t.length + maxCost.toInt() + 2) }
        var budget = MAX_CORRECTION_CANDIDATES
        fun consider(w: String, freq: Double, userCount: Int) {
            if (w == t || w.isEmpty() || user.isBlocked(w)) return
            val cost = distance(t, w, maxCost, hints, matrix)
            if (cost > maxCost) return
            val sim = 1.0 - cost / (maxCost + 0.5)
            val prefixBonus = if (w[0] == t[0]) 0.1 else 0.0
            val score = sim * 0.6 + freq * 0.3 + prefixBonus + userBoost(userCount) + (ctxScores[w] ?: 0.0) * 0.5
            if (score > (out[w] ?: -1.0)) out[w] = score
        }
        val lo = (t.length - maxCost.toInt()).coerceAtLeast(1)
        for (len in lo..t.length + maxCost.toInt()) {
            val bucket = byLength[len] ?: continue
            var n = 0
            for (w in bucket) {
                if (n++ >= MAX_PER_BUCKET || budget-- <= 0) break
                consider(w, (base[w] ?: 0) / 255.0, userWords[w] ?: 0)
            }
        }
        for ((w, c) in userWords) if (abs(w.length - t.length) <= maxCost.toInt()) consider(w, 0.3, c)
        return out.entries.sortedByDescending { it.value }.take(limit).map { ScoredWord(it.key, it.value) }
    }

    /**
     * Probability of each next letter after [prefix], from base and personal word frequencies. This is what lets the
     * keyboard decide between two neighboring keys when a tap lands near their shared edge. Empty if unknown.
     */
    fun letterPrior(prefix: String): Map<Char, Double> {
        val p = prefix.lowercase()
        if (p.isEmpty() || p.any { it !in 'a'..'z' && it != '\'' }) return emptyMap()
        val weights = DoubleArray(26)
        for (k in 0 until 26) {
            val c = 'a' + k
            val lo = lowerBound(p + c)
            val hi = lowerBound(p + (c + 1))
            weights[k] = prefixWeight[hi] - prefixWeight[lo]
        }
        // Snapshot only: bounded binary search plus a short scan, never a walk over the whole personal dictionary.
        val ui = userIndex
        var u = userLowerBound(ui, p)
        var seen = 0
        while (u < ui.words.size && ui.words[u].startsWith(p) && seen < 300) {
            val w = ui.words[u]
            if (w.length > p.length) {
                val c = w[p.length]
                if (c in 'a'..'z') weights[c - 'a'] += 1.0 + userBoost(ui.counts[u]) * 3.0
            }
            u++; seen++
        }
        val total = weights.sum()
        if (total <= 0.0) return emptyMap()
        val out = HashMap<Char, Double>()
        for (k in 0 until 26) if (weights[k] > 0.0) out['a' + k] = weights[k] / total
        return out
    }

    companion object {
        private const val MAX_PER_BUCKET = 2500
        private const val MAX_CORRECTION_CANDIDATES = 9000
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
     * Optimal string alignment distance where substituting a neighboring QWERTY key costs 0.5 (0.25 if it is the touch hint), a
     * transposition 1 and everything else 1. Returns a value > [limit] early if the words are too far apart.
     */
    private fun distance(a: String, b: String, limit: Double, hints: List<Char?>?, d: Array<DoubleArray>): Double {
        val n = a.length
        val m = b.length
        if (abs(n - m) > limit) return limit + 1
        for (i in 0..n) d[i][0] = i.toDouble()
        for (j in 0..m) d[0][j] = j.toDouble()
        for (i in 1..n) {
            var rowMin = Double.MAX_VALUE
            for (j in 1..m) {
                val sub = when {
                    a[i - 1] == b[j - 1] -> 0.0
                    hints != null && hints.getOrNull(i - 1) == b[j - 1] -> 0.25
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

/**
 * Shared state between the touch layer and the suggestion engine (both live in the same process): the engine for
 * synchronous letter priors and the per-letter runner-up keys of the word being typed.
 */
object TouchModel {
    @Volatile var engine: SuggestionEngine? = null

    private val lock = Any()
    private val typed = StringBuilder()
    private val runnerUps = ArrayList<Char?>()

    fun onLetter(letter: Char, runnerUp: Char?) = synchronized(lock) {
        typed.append(letter.lowercaseChar())
        runnerUps.add(runnerUp?.lowercaseChar())
        if (typed.length > 40) { typed.deleteCharAt(0); runnerUps.removeAt(0) }
    }

    fun onDelete() = synchronized(lock) {
        if (typed.isNotEmpty()) { typed.deleteCharAt(typed.length - 1); runnerUps.removeAt(runnerUps.size - 1) }
    }

    fun clear() = synchronized(lock) { typed.setLength(0); runnerUps.clear() }

    /** The runner-up keys, but only if they belong to exactly [word]. */
    fun hintsFor(word: String): List<Char?>? = synchronized(lock) {
        if (word.lowercase() == typed.toString()) ArrayList(runnerUps) else null
    }
}
