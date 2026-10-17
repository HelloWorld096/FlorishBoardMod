package dev.patrickgold.florisboard.ime.nlp

import android.content.Context
import dev.patrickgold.florisboard.ime.editor.EditorRange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID

@Serializable
data class SnippetItem(
    val id: String = UUID.randomUUID().toString(),
    val shortcut: String,
    val value: String,
)

data class ExpandedSnippet(
    val text: String,
    val cursorOffset: Int?,
)

object SnippetManager {
    private val lock = Any()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private var file: File? = null
    private var tmpFile: File? = null
    private var hasPendingChanges = false

    private val _snippetsFlow = MutableStateFlow<List<SnippetItem>>(emptyList())
    val snippetsFlow: StateFlow<List<SnippetItem>> = _snippetsFlow.asStateFlow()

    fun init(context: Context) {
        synchronized(lock) {
            if (file != null) return
            val dir = context.applicationContext.filesDir
            file = File(dir, "snippets.json")
            tmpFile = File(dir, "snippets.json.tmp")
            load()
        }
    }

    private fun load() {
        val f = file ?: return
        val tf = tmpFile
        val target = when {
            f.exists() -> f
            tf != null && tf.exists() -> tf
            else -> null
        }
        val diskList = if (target != null) {
            try {
                json.decodeFromString<List<SnippetItem>>(target.readText())
            } catch (_: Exception) {
                emptyList()
            }
        } else emptyList()

        if (hasPendingChanges) {
            val memory = _snippetsFlow.value
            val merged = memory.toMutableList()
            for (item in diskList) {
                if (merged.none { it.id == item.id || it.shortcut.equals(item.shortcut, ignoreCase = true) }) {
                    merged.add(item)
                }
            }
            _snippetsFlow.value = merged
            hasPendingChanges = false
            save()
            return
        }

        if (diskList.isNotEmpty()) {
            _snippetsFlow.value = diskList
            return
        }

        // Default snippets
        val defaults = listOf(
            SnippetItem(shortcut = "!email", value = "user@example.com"),
            SnippetItem(shortcut = "!shrug", value = "¯\\_(ツ)_/¯"),
            SnippetItem(shortcut = "!lenny", value = "( ͡° ͜ʖ ͡°)"),
            SnippetItem(shortcut = "!tableflip", value = "(╯°□°)╯︵ ┻━┻"),
            SnippetItem(shortcut = "!disapproval", value = "ಠ_ಠ"),
        )
        _snippetsFlow.value = defaults
        save()
    }

    private fun save() {
        scope.launch {
            val f: File
            val tf: File
            val text: String
            synchronized(lock) {
                f = file ?: return@launch
                tf = tmpFile ?: return@launch
                text = try {
                    json.encodeToString(_snippetsFlow.value)
                } catch (_: Exception) {
                    return@launch
                }
            }
            try {
                tf.writeText(text)
                if (!tf.renameTo(f)) {
                    f.delete()
                    tf.renameTo(f)
                }
            } catch (_: Exception) {
            }
        }
    }

    fun getAll(): List<SnippetItem> = _snippetsFlow.value

    fun addOrUpdate(id: String?, shortcut: String, value: String): SnippetItem {
        val trimmed = shortcut.trim()
        val s = if (trimmed.startsWith("!")) trimmed else "!$trimmed"
        val item = if (id != null) {
            SnippetItem(id = id, shortcut = s, value = value)
        } else {
            SnippetItem(shortcut = s, value = value)
        }
        synchronized(lock) {
            val current = _snippetsFlow.value.toMutableList()
            val idx = current.indexOfFirst { it.id == item.id }
            if (idx >= 0) {
                current[idx] = item
            } else {
                current.add(0, item)
            }
            _snippetsFlow.value = current
            if (file == null) {
                hasPendingChanges = true
            } else {
                save()
            }
        }
        return item
    }

    fun delete(id: String) {
        synchronized(lock) {
            val current = _snippetsFlow.value.filterNot { it.id == id }
            _snippetsFlow.value = current
            if (file == null) {
                hasPendingChanges = true
            } else {
                save()
            }
        }
    }

    fun expand(raw: String): String {
        return expandWithCursor(raw).text
    }

    fun expandWithCursor(raw: String): ExpandedSnippet {
        var res = raw
        if (res.contains("{date}")) {
            res = res.replace("{date}", LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy")))
        }
        if (res.contains("{time}")) {
            res = res.replace("{time}", LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")))
        }
        if (res.contains("{datetime}")) {
            res = res.replace("{datetime}", LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")))
        }
        if (res.contains("{uuid}")) {
            res = res.replace("{uuid}", UUID.randomUUID().toString())
        }
        val cursorIdx = res.indexOf("{cursor}")
        val finalText = if (cursorIdx >= 0) {
            res.replace("{cursor}", "")
        } else {
            res
        }
        val cursorOffset = if (cursorIdx >= 0) cursorIdx else null
        return ExpandedSnippet(finalText, cursorOffset)
    }

    fun findCandidate(text: String, cursor: Int, offset: Int): RangedWordCandidate? {
        if (cursor <= 0 || cursor > text.length) return null
        val windowStart = (cursor - 30).coerceAtLeast(0)
        val candidate = text.substring(windowStart, cursor)

        for (snippet in _snippetsFlow.value) {
            if (candidate.endsWith(snippet.shortcut, ignoreCase = true)) {
                val start = cursor - snippet.shortcut.length
                val (expandedText, cursorOffset) = expandWithCursor(snippet.value)
                return RangedWordCandidate(
                    text = expandedText,
                    originalText = snippet.shortcut,
                    replaceRange = EditorRange(start + offset, cursor + offset),
                    secondaryText = snippet.shortcut,
                    cursorOffset = cursorOffset,
                )
            }
        }
        return null
    }
}
