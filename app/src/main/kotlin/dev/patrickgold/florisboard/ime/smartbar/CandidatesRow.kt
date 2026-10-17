/*
 * Copyright (C) 2024-2025 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.ime.smartbar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import dev.patrickgold.florisboard.ime.nlp.ColorSuggestionCandidate
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.nlp.ClipboardSuggestionCandidate
import dev.patrickgold.florisboard.ime.nlp.MathSuggestionCandidate
import dev.patrickgold.florisboard.ime.nlp.SelectionCountCandidate
import dev.patrickgold.florisboard.ime.nlp.SuggestionCandidate
import org.florisboard.lib.android.showShortToastSync
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.florisboard.nlpManager
import dev.patrickgold.florisboard.subtypeManager
import dev.patrickgold.jetpref.datastore.model.observeAsState
import org.florisboard.lib.compose.conditional
import org.florisboard.lib.compose.florisHorizontalScroll
import org.florisboard.lib.snygg.SnyggSelector
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.SnyggColumn
import org.florisboard.lib.snygg.ui.SnyggIcon
import org.florisboard.lib.snygg.ui.SnyggRow
import org.florisboard.lib.snygg.ui.SnyggSpacer
import org.florisboard.lib.snygg.ui.SnyggText

val CandidatesRowScrollbarHeight = 2.dp

@Composable
fun CandidatesRow(modifier: Modifier = Modifier) {
    val prefs by FlorisPreferenceStore
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val nlpManager by context.nlpManager()
    val subtypeManager by context.subtypeManager()

    val displayMode by prefs.suggestion.displayMode.observeAsState()
    val candidates by nlpManager.activeCandidatesFlow.collectAsState()

    SnyggRow(
        elementName = FlorisImeUi.SmartbarCandidatesRow.elementName,
        modifier = modifier
            .fillMaxSize()
            .conditional(displayMode == CandidatesDisplayMode.DYNAMIC_SCROLLABLE && candidates.size > 1) {
                florisHorizontalScroll(scrollbarHeight = CandidatesRowScrollbarHeight)
            },
        horizontalArrangement = if (candidates.size > 1 || candidates.firstOrNull() is ColorSuggestionCandidate) {
            Arrangement.Start
        } else {
            Arrangement.Center
        },
    ) {
        if (candidates.isNotEmpty()) {
            val candidateModifier = if (candidates.size == 1) {
                if (candidates.first() is ColorSuggestionCandidate) {
                    Modifier
                        .fillMaxHeight()
                        .wrapContentWidth()
                } else {
                    Modifier
                        .fillMaxHeight()
                        .weight(1f, fill = false)
                }
            } else {
                Modifier
                    .fillMaxHeight()
                    .conditional(displayMode == CandidatesDisplayMode.CLASSIC) {
                        weight(1f)
                    }
                    .conditional(displayMode != CandidatesDisplayMode.CLASSIC) {
                        wrapContentWidth().widthIn(max = 160.dp)
                    }
            }
            val list = when (displayMode) {
                CandidatesDisplayMode.CLASSIC -> candidates.subList(0, 3.coerceAtMost(candidates.size))
                else -> candidates
            }
            for ((n, candidate) in list.withIndex()) {
                if (n > 0) {
                    SnyggSpacer(
                        elementName = FlorisImeUi.SmartbarCandidateSpacer.elementName,
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight(0.6f)
                            .align(Alignment.CenterVertically),
                    )
                }
                CandidateItem(
                    modifier = when {
                        candidate is ColorSuggestionCandidate || candidate is SelectionCountCandidate -> {
                            Modifier.fillMaxHeight().wrapContentWidth()
                        }
                        candidate is MathSuggestionCandidate && candidates.size > 1 &&
                            displayMode != CandidatesDisplayMode.CLASSIC -> {
                            Modifier.fillMaxHeight().wrapContentWidth().widthIn(max = 240.dp)
                        }
                        else -> {
                            candidateModifier
                        }
                    },
                    candidate = candidate,
                    displayMode = displayMode,
                    onClick = {
                        val item = candidates.getOrNull(n) ?: candidate
                        keyboardManager.commitCandidate(item)
                    },
                    onLongPress = {
                        val candidateItem = candidates.getOrNull(n) ?: candidate
                        when {
                            // Long press on a calculator suggestion inserts only the result.
                            candidateItem is MathSuggestionCandidate -> {
                                keyboardManager.commitMathResultOnly(candidateItem)
                                true
                            }
                            candidateItem is SelectionCountCandidate -> {
                                keyboardManager.clipboardManager.addNewPlaintext(candidateItem.text.toString())
                                context.showShortToastSync("Copied count summary")
                                true
                            }
                            candidateItem.isEligibleForUserRemoval -> {
                                nlpManager.removeSuggestion(subtypeManager.activeSubtype, candidateItem)
                            }
                            else -> false
                        }
                    },
                    longPressDelay = prefs.keyboard.longPressDelay.get().toLong(),
                )
            }
        }
    }
}

@Composable
private fun CandidateItem(
    candidate: SuggestionCandidate,
    displayMode: CandidatesDisplayMode,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = { },
    onLongPress: () -> Boolean = { false },
    longPressDelay: Long,
) = with(LocalDensity.current) {
    var isPressed by remember { mutableStateOf(false) }
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongPress by rememberUpdatedState(onLongPress)

    // A calculator chip always shows its full expression. If it doesn't fit it scrolls sideways, resting on the end
    // (the result) so the answer is visible and the start of the expression is a swipe away.
    val isMath = candidate is MathSuggestionCandidate
    val mathScroll = rememberScrollState()
    if (isMath) {
        LaunchedEffect(Unit) {
            snapshotFlow { mathScroll.maxValue }.collect { mathScroll.scrollTo(it) }
        }
    }

    val elementName = if (candidate is ClipboardSuggestionCandidate) {
        FlorisImeUi.SmartbarCandidateClip
    } else {
        FlorisImeUi.SmartbarCandidateWord
    }.elementName
    val attributes = mapOf("auto-commit" to if (candidate.isEligibleForAutoCommit) 1 else 0)
    val selector = if (isPressed) SnyggSelector.PRESSED else SnyggSelector.NONE

    SnyggRow(
        elementName = elementName,
        attributes = attributes,
        selector = selector,
        modifier = modifier
            .pointerInput(longPressDelay) {
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
                        if (currentOnLongPress()) {
                            upOrCancel = null
                            isPressed = false
                        }
                        waitForUpOrCancellation()?.let { if (it.pressed != it.previousPressed) it.consume() }
                    }
                    if (upOrCancel != null) {
                        currentOnClick()
                    }
                    isPressed = false
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (candidate is ColorSuggestionCandidate) {
            Box(
                modifier = Modifier
                    .padding(end = 6.dp)
                    .size(16.dp)
                    .background(Color(candidate.colorInt), shape = CircleShape)
                    .border(1.dp, Color.Gray.copy(alpha = 0.5f), shape = CircleShape),
            )
        } else if (candidate.icon != null) {
            SnyggBox(
                elementName = "$elementName-icon",
                attributes = attributes,
                selector = selector,
            ) {
                SnyggIcon(imageVector = candidate.icon!!)
            }
        }
        SnyggColumn(
            modifier = (if (displayMode == CandidatesDisplayMode.CLASSIC) Modifier.weight(1f) else Modifier)
                .conditional(isMath) { horizontalScroll(mathScroll) },
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SnyggText(
                elementName = "$elementName-text",
                attributes = attributes,
                selector = selector,
                text = candidate.text.toString(),
            )
            if (candidate.secondaryText != null) {
                SnyggText(
                    elementName = "$elementName-secondary-text",
                    attributes = attributes,
                    selector = selector,
                    text = candidate.secondaryText!!.toString(),
                )
            }
        }
    }
}
