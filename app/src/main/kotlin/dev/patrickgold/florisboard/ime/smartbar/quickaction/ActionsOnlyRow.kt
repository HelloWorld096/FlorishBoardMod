/*
 * Copyright (C) 2026 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.ime.smartbar.quickaction

import android.view.KeyEvent
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.FlorisImeService
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.keyboardManager
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.SnyggRow
import org.florisboard.lib.snygg.ui.SnyggText
import org.florisboard.lib.snygg.ui.rememberSnyggThemeQuery

/**
 * Modifier state that is locked on by the actions-only row (Ctrl / Alt / Shift buttons). Like caps lock, a modifier
 * stays on until its button is tapped again (or a new text field is started). Locked buttons show accent-colored text.
 */
object LatchedModifiers {
    var ctrl by mutableStateOf(false)
    var alt by mutableStateOf(false)
    var shift by mutableStateOf(false)

    fun reset() {
        ctrl = false
        alt = false
        shift = false
    }
}

private sealed interface ActionsOnlyItem {
    /** A regular key rendered through the stock [QuickActionButton] (icon, tooltip, hold-to-repeat). */
    class Key(val action: QuickAction.InsertKey) : ActionsOnlyItem

    /** A text button with a custom tap handler. [active] is used to show latched modifiers. */
    class Text(val label: String, val active: () -> Boolean = { false }, val onTap: () -> Unit) : ActionsOnlyItem
}

private fun key(data: dev.patrickgold.florisboard.ime.keyboard.KeyData) =
    ActionsOnlyItem.Key(QuickAction.InsertKey(data))

@Composable
fun ActionsOnlyRow(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val evaluator by keyboardManager.activeSmartbarEvaluator.collectAsState()
    var page by remember { mutableIntStateOf(0) }

    val pages = remember {
        listOf(
            // Page 1 (default): clipboard, cut, copy, select all, paste, left, right
            listOf(
                key(TextKeyData.IME_UI_MODE_CLIPBOARD),
                key(TextKeyData.CLIPBOARD_CUT),
                key(TextKeyData.CLIPBOARD_COPY),
                key(TextKeyData.CLIPBOARD_SELECT_ALL),
                key(TextKeyData.CLIPBOARD_PASTE),
                key(TextKeyData.ARROW_LEFT),
                key(TextKeyData.ARROW_RIGHT),
            ),
            // Page 2: ctrl, shift, forward delete, up, down, undo, redo
            listOf(
                ActionsOnlyItem.Text("Ctrl", { LatchedModifiers.ctrl }) { LatchedModifiers.ctrl = !LatchedModifiers.ctrl },
                ActionsOnlyItem.Text("Shift", { LatchedModifiers.shift }) { LatchedModifiers.shift = !LatchedModifiers.shift },
                key(TextKeyData.FORWARD_DELETE),
                key(TextKeyData.ARROW_UP),
                key(TextKeyData.ARROW_DOWN),
                key(TextKeyData.UNDO),
                key(TextKeyData.REDO),
            ),
            // Page 3: esc, alt, home, end, tab, page up, page down
            listOf(
                ActionsOnlyItem.Text("Esc") { keyboardManager.sendLatchedKeyEvent(KeyEvent.KEYCODE_ESCAPE) },
                ActionsOnlyItem.Text("Alt", { LatchedModifiers.alt }) { LatchedModifiers.alt = !LatchedModifiers.alt },
                ActionsOnlyItem.Text("Home") { keyboardManager.sendLatchedKeyEvent(KeyEvent.KEYCODE_MOVE_HOME) },
                ActionsOnlyItem.Text("End") { keyboardManager.sendLatchedKeyEvent(KeyEvent.KEYCODE_MOVE_END) },
                ActionsOnlyItem.Text("Tab") { keyboardManager.sendLatchedKeyEvent(KeyEvent.KEYCODE_TAB) },
                ActionsOnlyItem.Text("PgUp") { keyboardManager.sendLatchedKeyEvent(KeyEvent.KEYCODE_PAGE_UP) },
                ActionsOnlyItem.Text("PgDn") { keyboardManager.sendLatchedKeyEvent(KeyEvent.KEYCODE_PAGE_DOWN) },
            ),
        )
    }

    SnyggRow(
        elementName = FlorisImeUi.SmartbarSharedActionsRow.elementName,
        modifier = modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Every page has exactly SLOTS_PER_ROW cells of equal width (7 actions + the page switcher), so button size
        // and spacing are identical on all three rows.
        for (item in pages[page]) {
            ActionsOnlySlot {
                when (item) {
                    is ActionsOnlyItem.Key -> QuickActionButton(item.action, evaluator)
                    is ActionsOnlyItem.Text -> ActionsOnlyTextButton(item.label, item.active(), onTap = item.onTap)
                }
            }
        }
        // Page switcher, always the right-most button. 1 dot = page 1, 2 dots = page 2, 3 dots = page 3.
        ActionsOnlySlot {
            ActionsOnlyTextButton(
                label = "•".repeat(page + 1),
                active = false,
                small = false,
                onTap = { page = (page + 1) % pages.size },
            )
        }
    }
}

@Composable
private fun ActionsOnlyTextButton(label: String, active: Boolean, small: Boolean = true, onTap: () -> Unit) {
    val inputFeedbackController = FlorisImeService.inputFeedbackController()
    val elementName = FlorisImeUi.SmartbarActionKey.elementName
    // Locked state: only the text color changes (to the theme's accent, taken from the Enter key's background).
    val baseStyle = rememberSnyggThemeQuery(elementName)
    val accentStyle = rememberSnyggThemeQuery(FlorisImeUi.Key.elementName, mapOf(FlorisImeUi.Attr.Code to KeyCode.ENTER))
    SnyggBox(
        elementName = elementName,
        clickAndSemanticsModifier = Modifier
            .aspectRatio(1f)
            .pointerInput(onTap) {
                detectTapGestures(
                    onTap = {
                        inputFeedbackController?.keyPress(TextKeyData.UNSPECIFIED)
                        onTap()
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (small) {
            Text(
                text = label,
                color = if (active) accentStyle.background(default = Color(0xFF4CAF50)) else baseStyle.foreground(),
                fontSize = 11.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
            )
        } else {
            SnyggText(elementName = "$elementName-text", text = label)
        }
    }
}

@Composable
private fun RowScope.ActionsOnlySlot(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.weight(1f).fillMaxHeight(),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
