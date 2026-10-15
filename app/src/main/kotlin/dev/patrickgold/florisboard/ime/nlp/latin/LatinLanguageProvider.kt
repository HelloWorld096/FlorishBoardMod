/*
 * Copyright (C) 2022-2025 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.ime.nlp.latin

import android.content.Context
import dev.patrickgold.florisboard.appContext
import dev.patrickgold.florisboard.ime.core.Subtype
import dev.patrickgold.florisboard.ime.editor.EditorContent
import dev.patrickgold.florisboard.ime.nlp.SpellingProvider
import dev.patrickgold.florisboard.ime.nlp.SpellingResult
import dev.patrickgold.florisboard.ime.nlp.SuggestionCandidate
import dev.patrickgold.florisboard.ime.nlp.RangedWordCandidate
import dev.patrickgold.florisboard.ime.nlp.SuggestionProvider
import dev.patrickgold.florisboard.ime.nlp.WordSuggestionCandidate
import dev.patrickgold.florisboard.ime.editor.EditorRange
import dev.patrickgold.florisboard.lib.devtools.flogDebug
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.florisboard.lib.android.readText
import org.florisboard.lib.kotlin.guardedByLock

class LatinLanguageProvider(context: Context, private val userDictionary: UserDictionary) : SpellingProvider, SuggestionProvider {
    companion object {
        // Default user ID used for all subtypes, unless otherwise specified.
        // See `ime/core/Subtype.kt` Line 210 and 211 for the default usage
        const val ProviderId = "org.florisboard.nlp.providers.latin"

        fun isWordChar(c: Char) = c.isLetter() || c == '\''

        /**
         * Up to [max] whole words directly before the end of [text], oldest first. Words are only taken from the
         * current sentence-ish run: spaces between them are fine, any other punctuation or newline stops the walk.
         */
        fun previousWordsOf(text: String, max: Int): List<String> {
            val out = ArrayList<String>()
            var end = text.length
            while (out.size < max) {
                while (end > 0 && text[end - 1] == ' ') end--
                var start = end
                while (start > 0 && isWordChar(text[start - 1])) start--
                if (start == end) break
                out.add(0, text.substring(start, end))
                end = start
            }
            return out
        }
        private const val MAX_RESULTS = 6
    }

    private val appContext by context.appContext()

    private val wordData = guardedByLock { mutableMapOf<String, Int>() }
    private val wordDataSerializer = MapSerializer(String.serializer(), Int.serializer())
    @Volatile private var engine: SuggestionEngine? = null

    override val providerId = ProviderId

    override suspend fun create() {}

    override suspend fun preload(subtype: Subtype) = withContext(Dispatchers.IO) {
        ensureEngine()
        Unit
    }

    private suspend fun ensureEngine(): SuggestionEngine {
        engine?.let { return it }
        return wordData.withLock { wordData ->
            if (wordData.isEmpty()) {
                val rawData = appContext.assets.readText("ime/dict/data.json")
                wordData.putAll(Json.decodeFromString(wordDataSerializer, rawData))
            }
            engine ?: SuggestionEngine(wordData, userDictionary).also { engine = it; TouchModel.engine = it }
        }
    }

    override suspend fun spell(
        subtype: Subtype,
        word: String,
        precedingWords: List<String>,
        followingWords: List<String>,
        maxSuggestionCount: Int,
        allowPossiblyOffensive: Boolean,
        isPrivateSession: Boolean,
    ): SpellingResult {
        val eng = ensureEngine()
        if (word.length < 2 || word.any { !isWordChar(it) } || eng.isKnown(word)) return SpellingResult.validWord()
        val fixes = eng.correct(word, precedingWords.takeLast(UserDictionary.MAX_CONTEXT).filter { w -> w.isNotEmpty() && w.all { isWordChar(it) } }, maxSuggestionCount.coerceAtLeast(1)).map { it.word }
        return if (fixes.isEmpty()) SpellingResult.unspecified() else SpellingResult.typo(fixes.toTypedArray())
    }

    override suspend fun suggest(
        subtype: Subtype,
        content: EditorContent,
        maxCandidateCount: Int,
        allowPossiblyOffensive: Boolean,
        isPrivateSession: Boolean,
    ): List<SuggestionCandidate> = withContext(Dispatchers.Default) {
        val sel = content.localSelection
        if (sel.isNotValid || !sel.isCursorMode) return@withContext emptyList()
        val eng = ensureEngine()
        val text = content.text
        val cursor = sel.start.coerceIn(0, text.length)

        // The word the cursor sits in or touches, including the part after the cursor. This is what makes tapping into
        // an old, misspelled word produce corrections for the *whole* word.
        var start = cursor
        while (start > 0 && isWordChar(text[start - 1])) start--
        var end = cursor
        while (end < text.length && isWordChar(text[end])) end++
        val word = text.substring(start, end)
        val before = text.substring(0, start)
        val previousWords = previousWordsOf(before, UserDictionary.MAX_CONTEXT)

        val scored: List<ScoredWord> = when {
            word.isEmpty() -> {
                if (before.isEmpty() || before.last() == ' ' || before.last() == '\n') {
                    eng.nextWords(previousWords, MAX_RESULTS)
                } else emptyList()
            }
            else -> {
                val completions = eng.complete(word, previousWords, MAX_RESULTS)
                val corrections = if (eng.isKnown(word)) emptyList() else {
                    eng.correct(word, previousWords, MAX_RESULTS, TouchModel.hintsFor(word))
                }
                (completions + corrections).sortedByDescending { it.score }.distinctBy { it.word }.take(MAX_RESULTS)
            }
        }
        val composingMatchesWord = content.localComposing.let { it.isValid && it.start == start && it.end == end }
        val absolute = EditorRange(start + content.offset, end + content.offset)
        scored.map { sw ->
            val shown = matchCase(sw.word, word)
            if (word.isEmpty() || composingMatchesWord) {
                WordSuggestionCandidate(text = shown, confidence = sw.score.coerceIn(0.0, 1.0), sourceProvider = this@LatinLanguageProvider)
            } else {
                RangedWordCandidate(text = shown, originalText = word, replaceRange = absolute, confidence = sw.score.coerceIn(0.0, 1.0), sourceProvider = this@LatinLanguageProvider)
            }
        }
    }

    private fun matchCase(suggestion: String, typed: String): String = when {
        typed.length > 1 && typed.all { it.isUpperCase() || !it.isLetter() } -> suggestion.uppercase()
        typed.isNotEmpty() && typed[0].isUpperCase() -> suggestion.replaceFirstChar { it.uppercase() }
        else -> suggestion
    }

    override suspend fun notifySuggestionAccepted(subtype: Subtype, candidate: SuggestionCandidate) {
        flogDebug { candidate.toString() }
    }

    override suspend fun notifySuggestionReverted(subtype: Subtype, candidate: SuggestionCandidate) {
        flogDebug { candidate.toString() }
    }

    override suspend fun removeSuggestion(subtype: Subtype, candidate: SuggestionCandidate): Boolean {
        userDictionary.remove(candidate.text.toString())
        return true
    }

    override suspend fun getListOfWords(subtype: Subtype): List<String> {
        return wordData.withLock { it.keys.toList() }
    }

    override suspend fun getFrequencyForWord(subtype: Subtype, word: String): Double {
        return wordData.withLock { it.getOrDefault(word, 0) / 255.0 }
    }

    override suspend fun destroy() {}
}
