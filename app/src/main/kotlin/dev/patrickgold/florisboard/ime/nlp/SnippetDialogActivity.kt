package dev.patrickgold.florisboard.ime.nlp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.clipboardManager
import dev.patrickgold.florisboard.ime.clipboard.provider.ItemType
import dev.patrickgold.florisboard.ime.theme.FlorisImeTheme
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import kotlinx.coroutines.delay
import org.florisboard.lib.snygg.ui.rememberSnyggThemeQuery

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

@Composable
private fun KeyboardThemedInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = 1,
    focusRequester: FocusRequester? = null,
    backgroundColor: Color,
    textColor: Color,
    accentColor: Color,
    borderColor: Color,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = if (isFocused) accentColor else textColor.copy(alpha = 0.7f),
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(backgroundColor, RoundedCornerShape(8.dp))
                .border(
                    width = if (isFocused) 1.5.dp else 1.dp,
                    color = if (isFocused) accentColor else borderColor,
                    shape = RoundedCornerShape(8.dp),
                )
                .padding(horizontal = 12.dp, vertical = if (singleLine) 10.dp else 8.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                minLines = minLines,
                maxLines = maxLines,
                textStyle = TextStyle(
                    color = textColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                ),
                cursorBrush = SolidColor(accentColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                    .onFocusChanged { isFocused = it.isFocused },
                decorationBox = { innerTextField ->
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = textColor.copy(alpha = 0.35f),
                            fontSize = 14.sp,
                        )
                    }
                    innerTextField()
                },
            )
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

    // Query active keyboard theme styles
    val windowStyle = rememberSnyggThemeQuery(FlorisImeUi.Window.elementName)
    val itemStyle = rememberSnyggThemeQuery(FlorisImeUi.ClipboardItem.elementName)
    val subheaderStyle = rememberSnyggThemeQuery(FlorisImeUi.SmartbarActionsEditorSubheader.elementName)
    val primaryBtnStyle = rememberSnyggThemeQuery(FlorisImeUi.ClipboardHistoryDisabledButton.elementName)

    val backgroundColor = windowStyle.background().let { if (it.isSpecified) it else Color(0xFF1E1E1E) }
    val surfaceColor = itemStyle.background().let { if (it.isSpecified) it else Color(0xFF2D2D2D) }
    val onSurfaceColor = itemStyle.foreground().let { if (it.isSpecified) it else Color.White }
    val secondaryColor = subheaderStyle.foreground().let { if (it.isSpecified) it else Color(0xFF03DAC6) }
    val primaryColor = primaryBtnStyle.background().let { if (it.isSpecified) it else Color(0xFF4CAF50) }
    val onPrimaryColor = primaryBtnStyle.foreground().let { if (it.isSpecified) it else Color.White }

    val fieldBg = if (backgroundColor.isSpecified && backgroundColor != Color.Transparent && backgroundColor != surfaceColor) {
        backgroundColor
    } else {
        surfaceColor.copy(alpha = 0.5f)
    }

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
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 400.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { /* Capture click inside card */ },
                )
                .shadow(elevation = 8.dp, shape = RoundedCornerShape(16.dp))
                .background(surfaceColor, RoundedCornerShape(16.dp))
                .border(1.dp, onSurfaceColor.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                .padding(20.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Header: Title & Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = if (id == null) "New Snippet" else "Edit Snippet",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = onSurfaceColor,
                    )
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(),
                                onClick = onDismiss,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = onSurfaceColor.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Shortcut Input Field
                KeyboardThemedInputField(
                    value = shortcut,
                    onValueChange = { shortcut = it },
                    label = "Shortcut trigger",
                    placeholder = "e.g. !mycode",
                    singleLine = true,
                    focusRequester = focusRequester,
                    backgroundColor = fieldBg,
                    textColor = onSurfaceColor,
                    accentColor = secondaryColor,
                    borderColor = onSurfaceColor.copy(alpha = 0.18f),
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Expansion Value Input Field
                KeyboardThemedInputField(
                    value = value,
                    onValueChange = { value = it },
                    label = "Expansion value",
                    placeholder = "Macro expansion text",
                    singleLine = false,
                    minLines = 3,
                    maxLines = 6,
                    backgroundColor = fieldBg,
                    textColor = onSurfaceColor,
                    accentColor = secondaryColor,
                    borderColor = onSurfaceColor.copy(alpha = 0.18f),
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Dynamic Macro Tokens
                Text(
                    text = "Dynamic macros:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = onSurfaceColor.copy(alpha = 0.65f),
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
                                .background(fieldBg, RoundedCornerShape(8.dp))
                                .border(1.dp, onSurfaceColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(),
                                ) { value += token }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = token,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = secondaryColor,
                            )
                        }
                    }

                    val latestClip = unfilteredHistory.all.firstOrNull { it.type == ItemType.TEXT }?.stringRepresentation()
                    if (!latestClip.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .background(secondaryColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                .border(1.dp, secondaryColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(),
                                ) { value += latestClip }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = "Paste clip",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = secondaryColor,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Actions: Cancel & Save
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .border(1.dp, onSurfaceColor.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
                            .clip(RoundedCornerShape(20.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(),
                                onClick = onDismiss,
                            )
                            .padding(horizontal = 18.dp, vertical = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Cancel",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = onSurfaceColor.copy(alpha = 0.85f),
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    val isValid = shortcut.trim().isNotBlank() && value.isNotBlank()
                    val saveBg = if (isValid) primaryColor else primaryColor.copy(alpha = 0.35f)
                    val saveTextColor = if (isValid) onPrimaryColor else onPrimaryColor.copy(alpha = 0.5f)

                    Box(
                        modifier = Modifier
                            .background(saveBg, RoundedCornerShape(20.dp))
                            .clip(RoundedCornerShape(20.dp))
                            .clickable(
                                enabled = isValid,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(),
                            ) {
                                onSave(id, shortcut.trim(), value)
                            }
                            .padding(horizontal = 22.dp, vertical = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Save",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = saveTextColor,
                        )
                    }
                }
            }
        }
    }
}
