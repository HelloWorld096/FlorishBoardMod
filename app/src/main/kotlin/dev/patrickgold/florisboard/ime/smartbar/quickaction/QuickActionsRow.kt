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

package dev.patrickgold.florisboard.ime.smartbar.quickaction

import android.annotation.SuppressLint
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.editorInstance
import dev.patrickgold.florisboard.ime.smartbar.SmartbarLayout
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.jetpref.datastore.model.observeAsState
import org.florisboard.lib.android.showShortToastSync
import org.florisboard.lib.snygg.SnyggSelector
import org.florisboard.lib.snygg.ui.SnyggColumn
import org.florisboard.lib.snygg.ui.SnyggRow
import org.florisboard.lib.snygg.ui.SnyggText

internal val ToggleOverflowPanelAction = QuickAction.InsertKey(TextKeyData.TOGGLE_ACTIONS_OVERFLOW)

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun QuickActionsRow(
    elementName: String,
    modifier: Modifier = Modifier,
) = with(LocalDensity.current) {
    val prefs by FlorisPreferenceStore
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val editorInstance by context.editorInstance()

    val flipToggles by prefs.smartbar.flipToggles.observeAsState()
    val evaluator by keyboardManager.activeSmartbarEvaluator.collectAsState()
    val smartbarLayout by prefs.smartbar.layout.observeAsState()
    val actionArrangement by prefs.smartbar.actionArrangement.observeAsState()
    val sharedActionsExpanded by prefs.smartbar.sharedActionsExpanded.observeAsState()
    val activeContent by editorInstance.activeContentFlow.collectAsState()

    val isSelection = activeContent.selection.isSelectionMode && activeContent.selectedText.isNotEmpty()
    val selectedText = if (isSelection) activeContent.selectedText else ""
    val words = remember(selectedText) {
        if (selectedText.isBlank()) 0 else selectedText.trim().split(Regex("""\s+""")).count { it.isNotEmpty() }
    }
    val chars = remember(selectedText) { selectedText.length }
    val charsNoSpaces = remember(selectedText) { selectedText.count { !it.isWhitespace() } }
    val lines = remember(selectedText) { if (selectedText.isEmpty()) 0 else selectedText.lines().size }

    val dynamicActions = remember(smartbarLayout, actionArrangement) {
        if (smartbarLayout == SmartbarLayout.ACTIONS_ONLY && actionArrangement.stickyAction != null) {
            buildList {
                add(actionArrangement.stickyAction!!)
                addAll(actionArrangement.dynamicActions)
            }
        } else {
            actionArrangement.dynamicActions
        }
    }
    val showOverflowAction = actionArrangement.stickyAction != null ||
        smartbarLayout != SmartbarLayout.SUGGESTIONS_ACTIONS_SHARED || !sharedActionsExpanded

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val width = constraints.maxWidth.toDp()
        val height = constraints.maxHeight.toDp()
        val reservedChipWidth = if (isSelection) 120.dp else 0.dp
        val availableWidth = (width - reservedChipWidth).coerceAtLeast(0.dp)
        val numActionsToShow = ((availableWidth / height).toInt() - (if (showOverflowAction) 1 else 0)).coerceAtLeast(0)
        val visibleActions = dynamicActions
            .subList(0, numActionsToShow.coerceAtMost(dynamicActions.size))

        SideEffect {
            keyboardManager.smartbarVisibleDynamicActionsCount =
                if (smartbarLayout == SmartbarLayout.ACTIONS_ONLY && actionArrangement.stickyAction != null) {
                    numActionsToShow - 1
                } else {
                    numActionsToShow
                }.coerceAtLeast(0)
        }

        SnyggRow(
            elementName = elementName,
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            if (showOverflowAction && flipToggles) {
                QuickActionButton(ToggleOverflowPanelAction, evaluator)
            }
            if (isSelection) {
                SelectionCountChip(
                    words = words,
                    chars = chars,
                    lines = lines,
                    charsNoSpaces = charsNoSpaces,
                    longPressDelay = prefs.keyboard.longPressDelay.get().toLong(),
                )
            }
            for (action in visibleActions) {
                QuickActionButton(action, evaluator)
            }
            if (showOverflowAction && !flipToggles) {
                QuickActionButton(ToggleOverflowPanelAction, evaluator)
            }
        }
    }
}

@Composable
private fun SelectionCountChip(
    words: Int,
    chars: Int,
    lines: Int,
    charsNoSpaces: Int,
    longPressDelay: Long,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val wordsLabel = if (words == 1) "1 word" else "$words words"
    val charsLabel = if (chars == 1) "1 char" else "$chars chars"
    val linesLabel = if (lines == 1) "1 line" else "$lines lines"

    var isPressed by remember { mutableStateOf(false) }
    val elementName = FlorisImeUi.SmartbarCandidateWord.elementName
    val attributes = mapOf("auto-commit" to 0)
    val selector = if (isPressed) SnyggSelector.PRESSED else SnyggSelector.NONE

    SnyggRow(
        elementName = elementName,
        attributes = attributes,
        selector = selector,
        modifier = modifier
            .fillMaxHeight()
            .pointerInput(longPressDelay, wordsLabel, charsLabel, linesLabel, charsNoSpaces) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    isPressed = true
                    if (down.pressed != down.previousPressed) down.consume()
                    var upOrCancel: PointerInputChange? = null
                    try {
                        upOrCancel = withTimeout(longPressDelay) {
                            waitForUpOrCancellation()
                        }
                        upOrCancel?.let { if (it.pressed != it.previousPressed) it.consume() }
                    } catch (_: PointerEventTimeoutCancellationException) {
                        keyboardManager.clipboardManager.addNewPlaintext("$wordsLabel • $charsLabel")
                        context.showShortToastSync("Copied count summary")
                        upOrCancel = null
                        isPressed = false
                        waitForUpOrCancellation()?.let { if (it.pressed != it.previousPressed) it.consume() }
                    }
                    if (upOrCancel != null) {
                        val toastText = "$wordsLabel • $charsLabel ($charsNoSpaces without spaces) • $linesLabel"
                        context.showShortToastSync(toastText)
                    }
                    isPressed = false
                }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        SnyggColumn(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SnyggText(
                elementName = "$elementName-text",
                attributes = attributes,
                selector = selector,
                text = "$wordsLabel • $charsLabel",
            )
            SnyggText(
                elementName = "$elementName-secondary-text",
                attributes = attributes,
                selector = selector,
                text = linesLabel,
            )
        }
    }
}
