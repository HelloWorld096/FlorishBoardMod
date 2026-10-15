package dev.patrickgold.florisboard.ime.nlp.latin

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * On-device word history. Stores how often each word was typed and which word tends to follow which. Never leaves the
 * device and is only ever written to while the suggestion bar is on (the gate lives in NlpManager.learnWord).
 */
class UserDictionary(context: Context) {
    @Serializable
    private data class Data(
        val words: MutableMap<String, Int> = mutableMapOf(),
        // Legacy single-word contexts, migrated into [ngrams] on load.
        val bigrams: MutableMap<String, MutableMap<String, Int>> = mutableMapOf(),
        // Context (1 to 3 previous words joined by a space) -> word that followed -> count.
        val ngrams: MutableMap<String, MutableMap<String, Int>> = mutableMapOf(),
        val blocked: MutableSet<String> = mutableSetOf(),
    )

    private val file = File(context.filesDir, "user_dictionary.json")
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()
    private var data = load()
    private var saveJob: Job? = null

    private fun load(): Data = try {
        if (file.exists()) {
            json.decodeFromString(Data.serializer(), file.readText()).also { d ->
                for ((k, v) in d.bigrams) {
                    val target = d.ngrams.getOrPut(k) { mutableMapOf() }
                    for ((w, c) in v) target[w] = (target[w] ?: 0) + c
                }
                d.bigrams.clear()
            }
        } else Data()
    } catch (_: Exception) {
        Data()
    }

    /** [previous] holds the words before [word], oldest first. The last [MAX_CONTEXT] of them are used as context. */
    fun learn(word: String, previous: List<String>) {
        val w = word.lowercase()
        if (w.length < 2 || w.length > 40) return
        synchronized(lock) {
            if (w in data.blocked) return
            data.words[w] = (data.words[w] ?: 0) + 1
            val ctx = previous.takeLast(MAX_CONTEXT).map { it.lowercase() }
            for (k in 1..ctx.size) {
                val words = ctx.takeLast(k)
                if (words.any { it in data.blocked }) break
                val next = data.ngrams.getOrPut(words.joinToString(" ")) { mutableMapOf() }
                next[w] = (next[w] ?: 0) + 1
            }
            if (data.words.size > MAX_WORDS || data.ngrams.size > MAX_CONTEXTS) prune()
        }
        scheduleSave()
    }

    fun remove(word: String) {
        val w = word.lowercase()
        synchronized(lock) {
            data.words.remove(w)
            data.ngrams.keys.removeAll { key -> key.split(' ').contains(w) }
            data.ngrams.values.forEach { it.remove(w) }
            data.blocked.add(w)
        }
        scheduleSave()
    }

    fun clear() {
        synchronized(lock) { data = Data() }
        scheduleSave()
    }

    fun count(word: String): Int = synchronized(lock) { data.words[word.lowercase()] ?: 0 }
    fun contains(word: String): Boolean = count(word) > 0
    fun isBlocked(word: String): Boolean = synchronized(lock) { word.lowercase() in data.blocked }

    /** Snapshot of all learned words with their counts. */
    fun words(): Map<String, Int> = synchronized(lock) { HashMap(data.words) }

    /** Words that followed exactly this [context] (1 to 3 words, oldest first) before, with counts. */
    fun followers(context: List<String>): Map<String, Int> = synchronized(lock) {
        data.ngrams[context.joinToString(" ") { it.lowercase() }]?.let { HashMap(it) } ?: emptyMap()
    }

    /** Runs [block] on every learned word without copying the map. Keep [block] short. */
    fun forEachWord(block: (String, Int) -> Unit) = synchronized(lock) {
        for ((w, c) in data.words) block(w, c)
    }

    /** The user's most used words overall. */
    fun topWords(limit: Int): List<Pair<String, Int>> = synchronized(lock) {
        data.words.entries.sortedByDescending { it.value }.take(limit).map { it.key to it.value }
    }

    private fun prune() {
        // Drop the long tail of words and contexts seen only once.
        data.words.entries.removeAll { it.value <= 1 }
        data.ngrams.entries.removeAll { (k, v) ->
            v.entries.removeAll { it.value <= 1 || it.key !in data.words }
            v.isEmpty() || k.split(' ').any { it !in data.words }
        }
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(3000)
            val text = synchronized(lock) { json.encodeToString(Data.serializer(), data) }
            try {
                file.writeText(text)
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        const val MAX_CONTEXT = 3
        private const val MAX_WORDS = 30_000
        private const val MAX_CONTEXTS = 80_000
    }
}
