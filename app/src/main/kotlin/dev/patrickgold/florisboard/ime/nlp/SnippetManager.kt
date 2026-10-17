package dev.patrickgold.florisboard.ime.nlp

import android.content.Context
import dev.patrickgold.florisboard.ime.editor.EditorRange
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

object SnippetManager {
    private val lock = Any()
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private var file: File? = null
    private var tmpFile: File? = null

    private val _snippetsFlow = MutableStateFlow<List<SnippetItem>>(emptyList())
    val snippetsFlow: StateFlow<List<SnippetItem>> = _snippetsFlow.asStateFlow()

    fun init(context: Context) {
        synchronized(lock) {
            if (file != null) return
            val dir = context.filesDir
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
        if (target != null) {
            try {
                val list = json.decodeFromString<List<SnippetItem>>(target.readText())
                if (list.isNotEmpty()) {
                    _snippetsFlow.value = list
                    return
                }
            } catch (_: Exception) {
            }
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

    private fun save() = synchronized(lock) {
        val f = file ?: return
        val tf = tmpFile ?: return
        try {
            val text = json.encodeToString(_snippetsFlow.value)
            tf.writeText(text)
            if (!tf.renameTo(f)) {
                f.delete()
                tf.renameTo(f)
            }
        } catch (_: Exception) {
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
        val current = _snippetsFlow.value.toMutableList()
        val idx = current.indexOfFirst { it.id == item.id }
        if (idx >= 0) {
            current[idx] = item
        } else {
            current.add(0, item)
        }
        _snippetsFlow.value = current
        save()
        return item
    }

    fun delete(id: String) {
        val current = _snippetsFlow.value.filterNot { it.id == id }
        _snippetsFlow.value = current
        save()
    }

    fun expand(raw: String): String {
        var res = raw
        if (res.contains("{date}")) {
            res = res.replace("{date}", LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy")))
        }
        if (res.contains("{time}")) {
            res = res.replace("{time}", LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")))
        }
        if (res.contains("{datetime}")) {
            res = res.replace("{datetime}", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
        }
        if (res.contains("{uuid}")) {
            res = res.replace("{uuid}", UUID.randomUUID().toString())
        }
        return res
    }

    fun findCandidate(text: String, cursor: Int, offset: Int): RangedWordCandidate? {
        if (cursor <= 0 || cursor > text.length) return null
        val windowStart = (cursor - 30).coerceAtLeast(0)
        val candidate = text.substring(windowStart, cursor)

        for (snippet in _snippetsFlow.value) {
            if (candidate.endsWith(snippet.shortcut, ignoreCase = true)) {
                val start = cursor - snippet.shortcut.length
                val expanded = expand(snippet.value)
                return RangedWordCandidate(
                    text = expanded,
                    originalText = snippet.shortcut,
                    replaceRange = EditorRange(start + offset, cursor + offset),
                    secondaryText = snippet.shortcut,
                )
            }
        }
        return null
    }
}
