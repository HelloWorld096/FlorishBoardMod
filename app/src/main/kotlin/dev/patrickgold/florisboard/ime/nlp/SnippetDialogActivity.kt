package dev.patrickgold.florisboard.ime.nlp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.clipboardManager
import dev.patrickgold.florisboard.ime.clipboard.provider.ItemType
import dev.patrickgold.florisboard.ime.theme.FlorisImeTheme
import kotlinx.coroutines.delay

class SnippetDialogActivity : ComponentActivity() {
    companion object {
        const val EXTRA_ID = "snippet_id"
        const val EXTRA_SHORTCUT = "snippet_shortcut"
        const val EXTRA_VALUE = "snippet_value"

        fun newIntent(
            context: Context,
            id: String? = null,
            shortcut: String? = null,
            value: String? = null,
        ): Intent {
            return Intent(context, SnippetDialogActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_ID, id)
                putExtra(EXTRA_SHORTCUT, shortcut)
                putExtra(EXTRA_VALUE, value)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val snippetId = intent.getStringExtra(EXTRA_ID)
        val initialShortcut = intent.getStringExtra(EXTRA_SHORTCUT) ?: "!"
        val initialValue = intent.getStringExtra(EXTRA_VALUE) ?: ""

        setContent {
            FlorisImeTheme {
                SnippetDialogContent(
                    id = snippetId,
                    initialShortcut = initialShortcut,
                    initialValue = initialValue,
                    onDismiss = { finish() },
                    onSave = { id, shortcut, value ->
                        SnippetManager.addOrUpdate(id, shortcut, value)
                        finish()
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SnippetDialogContent(
    id: String?,
    initialShortcut: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onSave: (id: String?, shortcut: String, value: String) -> Unit,
) {
    var shortcut by remember(initialShortcut) { mutableStateOf(initialShortcut) }
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current
    val clipboardManager by context.clipboardManager()
    val unfilteredHistory by clipboardManager.historyFlow.collectAsState()

    LaunchedEffect(Unit) {
        delay(150)
        focusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            )
            .padding(16.dp)
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { /* Capture click inside card */ },
                ),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            ) {
                Text(
                    text = if (id == null) "Add Snippet" else "Edit Snippet",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = shortcut,
                    onValueChange = { shortcut = it },
                    label = { Text("Shortcut trigger") },
                    placeholder = { Text("e.g. !mycode") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("Expansion value") },
                    placeholder = { Text("Macro expansion text") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Insert dynamic macro:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf("{date}", "{time}", "{datetime}", "{uuid}").forEach { token ->
                        Box(
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(8.dp),
                                )
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { value += token }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = token,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }

                    val latestClip = unfilteredHistory.all.firstOrNull { it.type == ItemType.TEXT }?.stringRepresentation()
                    if (!latestClip.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.secondaryContainer,
                                    RoundedCornerShape(8.dp),
                                )
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { value += latestClip }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = "Paste clip",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = {
                            val trimmedShortcut = shortcut.trim()
                            if (trimmedShortcut.isNotBlank() && value.isNotBlank()) {
                                onSave(id, trimmedShortcut, value)
                            }
                        },
                        enabled = shortcut.trim().isNotBlank() && value.isNotBlank(),
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}
