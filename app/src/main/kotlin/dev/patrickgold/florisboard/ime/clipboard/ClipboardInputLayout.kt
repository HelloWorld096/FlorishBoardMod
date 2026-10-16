/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.ime.clipboard

import android.content.ContentUris
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.provider.MediaStore
import android.util.Size
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FilterListOff
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.ToggleOff
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.ContentPasteGo
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.clipboardManager
import dev.patrickgold.florisboard.editorInstance
import dev.patrickgold.florisboard.ime.nlp.SnippetDialogActivity
import dev.patrickgold.florisboard.ime.nlp.SnippetItem
import dev.patrickgold.florisboard.ime.nlp.SnippetManager
import dev.patrickgold.florisboard.ime.ImeUiMode
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardFileStorage
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardItem
import dev.patrickgold.florisboard.ime.clipboard.provider.ItemType
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.ime.media.KeyboardLikeButton
import dev.patrickgold.florisboard.ime.smartbar.AnimationDuration
import dev.patrickgold.florisboard.ime.smartbar.SmartbarSuggestionRowForPanels
import dev.patrickgold.florisboard.ime.smartbar.VerticalEnterTransition
import dev.patrickgold.florisboard.ime.smartbar.VerticalExitTransition
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import dev.patrickgold.florisboard.ime.text.key.KeyType
import androidx.compose.foundation.layout.fillMaxHeight
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.florisboard.lib.observeAsTransformingState
import dev.patrickgold.florisboard.lib.util.NetworkUtils
import dev.patrickgold.jetpref.datastore.model.observeAsState
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.florisboard.lib.android.AndroidKeyguardManager
import org.florisboard.lib.android.AndroidVersion
import org.florisboard.lib.android.showShortToastSync
import org.florisboard.lib.android.systemService
import org.florisboard.lib.compose.LocalLocalizedDateTimeFormatter
import org.florisboard.lib.compose.autoMirrorForRtl
import org.florisboard.lib.compose.florisHorizontalScroll
import org.florisboard.lib.compose.florisVerticalScroll
import org.florisboard.lib.compose.rippleClickable
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.snygg.SnyggQueryAttributes
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.SnyggButton
import org.florisboard.lib.snygg.ui.SnyggChip
import org.florisboard.lib.snygg.ui.SnyggColumn
import org.florisboard.lib.snygg.ui.SnyggIcon
import org.florisboard.lib.snygg.ui.SnyggIconButton
import org.florisboard.lib.snygg.ui.SnyggRow
import org.florisboard.lib.snygg.ui.SnyggText
import org.florisboard.lib.snygg.ui.rememberSnyggThemeQuery
import org.florisboard.lib.snygg.value.SnyggStaticColorValue

private val ItemWidth = 200.dp
private val DialogWidth = 240.dp

const val CLIPBOARD_HISTORY_NUM_GRID_COLUMNS_AUTO: Int = 0

@Composable
fun ClipboardInputLayout(
    modifier: Modifier = Modifier,
) {
    val prefs by FlorisPreferenceStore
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboardManager by context.clipboardManager()
    val keyboardManager by context.keyboardManager()
    val androidKeyguardManager = remember { context.systemService(AndroidKeyguardManager::class) }

    val deviceLocked = androidKeyguardManager.let { it.isDeviceLocked || it.isKeyguardLocked }
    val historyEnabled by prefs.clipboard.historyEnabled.observeAsState()

    var isFilterRowShown by remember { mutableStateOf(false) }
    val activeFilterTypes = remember { mutableStateSetOf<ItemType>() }

    val unfilteredHistory by clipboardManager.historyFlow.collectAsState()
    val filteredHistory = remember(unfilteredHistory, activeFilterTypes.toSet()) {
        if (activeFilterTypes.isEmpty()) {
            unfilteredHistory
        } else {
            unfilteredHistory.all
                .filter { activeFilterTypes.contains(it.type) }
                .let { ClipboardHistory(it) }
        }
    }

    val gridState = rememberLazyStaggeredGridState()
    var popupItem by remember(filteredHistory) { mutableStateOf<ClipboardItem?>(null) }
    var showClearAllHistory by remember { mutableStateOf(false) }
    var isSnippetsTab by remember { mutableStateOf(false) }
    var popupSnippet by remember { mutableStateOf<SnippetItem?>(null) }

    fun isPopupSurfaceActive() = popupItem != null || showClearAllHistory || popupSnippet != null

    LaunchedEffect(isFilterRowShown) {
        delay(AnimationDuration.toLong())
        if (!isFilterRowShown) {
            activeFilterTypes.clear()
        }
    }

    LaunchedEffect(activeFilterTypes.toSet()) {
        gridState.scrollToItem(0)
    }

    @Composable
    fun HeaderRow() {
        SnyggRow(FlorisImeUi.ClipboardHeader.elementName,
            modifier = Modifier
                .fillMaxWidth()
                .height(FlorisImeSizing.smartbarHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val sizeModifier = Modifier
                .sizeIn(maxHeight = FlorisImeSizing.smartbarHeight)
                .aspectRatio(1f)
            SnyggIconButton(
                elementName = FlorisImeUi.ClipboardHeaderButton.elementName,
                onClick = {
                    if (isSnippetsTab) {
                        isSnippetsTab = false
                    } else {
                        keyboardManager.activeState.imeUiMode = ImeUiMode.TEXT
                    }
                },
                modifier = sizeModifier,
            ) {
                SnyggIcon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                )
            }
            val headerBtnStyle = rememberSnyggThemeQuery(FlorisImeUi.ClipboardHeaderButton.elementName)
            val fg = headerBtnStyle.foreground().let { if (it == Color.Unspecified) Color.White else it }
            val borderCol = (headerBtnStyle.borderColor as? SnyggStaticColorValue)?.color
            val solidBg = if (isSnippetsTab) {
                fg
            } else {
                borderCol ?: fg.copy(alpha = 0.35f)
            }
            val fgColor = if (isSnippetsTab) {
                (headerBtnStyle.background as? SnyggStaticColorValue)?.color ?: Color.Black
            } else {
                fg
            }
            Box(
                modifier = sizeModifier
                    .padding(4.dp)
                    .background(solidBg, CircleShape)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(),
                    ) {
                        isSnippetsTab = !isSnippetsTab
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.AcUnit,
                    contentDescription = "Snippets",
                    modifier = Modifier.size(18.dp),
                    tint = fgColor,
                )
            }
            // Typing shortcuts: space (wide), full stop, comma, forward slash, backspace, enter
            KeyboardLikeButton(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                inputEventDispatcher = keyboardManager.inputEventDispatcher,
                keyData = TextKeyData.SPACE,
                elementName = FlorisImeUi.ClipboardHeaderButton.elementName,
            ) {
                SnyggIcon(imageVector = Icons.Default.SpaceBar)
            }
            for (symbol in listOf('.', ',', '/')) {
                KeyboardLikeButton(
                    modifier = sizeModifier,
                    inputEventDispatcher = keyboardManager.inputEventDispatcher,
                    keyData = TextKeyData(type = KeyType.CHARACTER, code = symbol.code, label = symbol.toString()),
                    elementName = FlorisImeUi.ClipboardHeaderButton.elementName,
                ) {
                    SnyggText(text = symbol.toString())
                }
            }
            KeyboardLikeButton(
                modifier = sizeModifier,
                inputEventDispatcher = keyboardManager.inputEventDispatcher,
                keyData = TextKeyData.DELETE,
                elementName = FlorisImeUi.ClipboardHeaderButton.elementName,
            ) {
                SnyggIcon(imageVector = Icons.AutoMirrored.Outlined.Backspace)
            }
            KeyboardLikeButton(
                modifier = sizeModifier,
                inputEventDispatcher = keyboardManager.inputEventDispatcher,
                keyData = TextKeyData(type = KeyType.ENTER_EDITING, code = KeyCode.ENTER, label = "enter"),
                elementName = FlorisImeUi.ClipboardHeaderButton.elementName,
            ) {
                SnyggIcon(imageVector = Icons.AutoMirrored.Filled.KeyboardReturn)
            }
        }
    }

    @Composable
    fun ClipItemView(
        elementName: String,
        item: ClipboardItem,
        contentScrollInsteadOfClip: Boolean,
        modifier: Modifier = Modifier,
    ) {
        val attributes = remember(item) {
            mapOf("type" to item.type.toString().lowercase())
        }
        SnyggBox(
            elementName = elementName,
            attributes = attributes,
            modifier = modifier.fillMaxWidth(),
            clickAndSemanticsModifier = Modifier.combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                enabled = popupItem == null,
                onLongClick = {
                    popupItem = item
                },
                onClick = {
                    clipboardManager.pasteItem(item)
                },
            ),
        ) {
            if (item.type == ItemType.IMAGE) {
                val id = ContentUris.parseId(item.uri!!)
                val file = ClipboardFileStorage.getFileForId(context, id)
                val bitmap = remember(id) {
                    runCatching {
                        check(file.exists()) { "Unable to resolve image at ${file.absolutePath}" }
                        val rawBitmap = BitmapFactory.decodeFile(file.absolutePath)
                        checkNotNull(rawBitmap) { "Unable to decode image at ${file.absolutePath}" }
                        rawBitmap.asImageBitmap()
                    }
                }
                if (bitmap.isSuccess) {
                    Image(
                        modifier = Modifier.fillMaxWidth(),
                        bitmap = bitmap.getOrThrow(),
                        contentDescription = null,
                        contentScale = ContentScale.FillWidth,
                    )
                } else {
                    SnyggText(
                        modifier = Modifier.fillMaxWidth(),
                        text = bitmap.exceptionOrNull()?.message ?: "Unknown error",
                    )
                }
            } else if (item.type == ItemType.VIDEO) {
                val id = ContentUris.parseId(item.uri!!)
                val file = ClipboardFileStorage.getFileForId(context, id)
                val bitmap = remember(id) {
                    runCatching {
                        check(file.exists()) { "Unable to resolve video at ${file.absolutePath}" }
                        val rawBitmap = if (AndroidVersion.ATLEAST_API29_Q) {
                            val dataRetriever = MediaMetadataRetriever()
                            dataRetriever.setDataSource(file.absolutePath)
                            val width = dataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                            val height = dataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                            ThumbnailUtils.createVideoThumbnail(file, Size(width!!.toInt(), height!!.toInt()), null)
                        } else {
                            @Suppress("DEPRECATION")
                            ThumbnailUtils.createVideoThumbnail(file.absolutePath, MediaStore.Video.Thumbnails.MINI_KIND)
                        }
                        checkNotNull(rawBitmap) { "Unable to decode video at ${file.absolutePath}" }
                        rawBitmap.asImageBitmap()
                    }
                }
                if (bitmap.isSuccess) {
                    Image(
                        modifier = Modifier.fillMaxWidth(),
                        bitmap = bitmap.getOrThrow(),
                        contentDescription = null,
                        contentScale = ContentScale.FillWidth,
                    )
                    Icon(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 4.dp, bottom = 4.dp)
                            .background(Color.White, CircleShape),
                        imageVector = Icons.Default.Videocam,
                        contentDescription = null,
                        tint = Color.Black,
                    )
                } else {
                    SnyggText(
                        modifier = Modifier.fillMaxWidth(),
                        text = bitmap.exceptionOrNull()?.message ?: "Unknown error",
                    )
                }
            } else {
                val text = item.stringRepresentation()
                Column {
                    ClipTextItemDescription(
                        elementName = FlorisImeUi.ClipboardItemDescription.elementName,
                        attributes = attributes,
                        text = text,
                    )
                    SnyggText(
                        modifier = Modifier
                            .fillMaxWidth()
                            .run { if (contentScrollInsteadOfClip) this.florisVerticalScroll() else this },
                        text = item.displayText(),
                    )
                }
            }
        }
    }

    @Composable
    fun HistoryMainView() {
        SnyggBox(FlorisImeUi.ClipboardContent.elementName,
            modifier = Modifier.fillMaxSize(),
        ) {
            val historyAlpha by animateFloatAsState(targetValue = if (isPopupSurfaceActive()) 0.12f else 1f)
            val staggeredGridCells by prefs.clipboard.historyNumGridColumns()
                .observeAsTransformingState { numGridColumns ->
                    if (numGridColumns == CLIPBOARD_HISTORY_NUM_GRID_COLUMNS_AUTO) {
                        StaggeredGridCells.Adaptive(160.dp)
                    } else {
                        StaggeredGridCells.Fixed(numGridColumns)
                    }
                }

            fun LazyStaggeredGridScope.clipboardItems(
                items: List<ClipboardItem>,
                key: String,
                @StringRes title: Int,
            ) {
                if (items.isNotEmpty()) {
                    item(key, span = StaggeredGridItemSpan.FullLine) {
                        ClipCategoryTitle(text = stringRes(title))
                    }
                    items(items) { item ->
                        ClipItemView(
                            elementName = FlorisImeUi.ClipboardItem.elementName,
                            item = item,
                            contentScrollInsteadOfClip = false,
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .matchParentSize()
                    .alpha(historyAlpha),
            ) {
                AnimatedVisibility(
                    visible = isFilterRowShown,
                    enter = VerticalEnterTransition,
                    exit = VerticalExitTransition,
                ) {
                    SnyggRow(
                        elementName = FlorisImeUi.ClipboardFilterRow.elementName,
                        modifier = Modifier.fillMaxWidth(),
                        clickAndSemanticsModifier = Modifier.florisHorizontalScroll(),
                    ) {
                        @Composable
                        fun FilterChip(
                            imageVector: ImageVector,
                            text: String,
                            itemType: ItemType,
                        ) {
                            val active = activeFilterTypes.contains(itemType)
                            val attributes = remember(active) {
                                mapOf(
                                    "state" to if (active) "active" else "inactive",
                                    "type" to itemType.toString().lowercase(),
                                )
                            }
                            SnyggChip(
                                elementName = FlorisImeUi.ClipboardFilterChip.elementName,
                                attributes = attributes,
                                onClick = {
                                    if (!activeFilterTypes.add(itemType)) {
                                        activeFilterTypes.remove(itemType)
                                    }
                                },
                                imageVector = imageVector,
                                text = text,
                            )
                        }

                        FilterChip(
                            imageVector = Icons.Default.TextFields,
                            text = "Text",
                            itemType = ItemType.TEXT,
                        )
                        FilterChip(
                            imageVector = Icons.Default.Image,
                            text = "Images",
                            itemType = ItemType.IMAGE,
                        )
                        FilterChip(
                            imageVector = Icons.Default.Movie,
                            text = "Videos",
                            itemType = ItemType.VIDEO,
                        )
                    }
                }
                SnyggBox(FlorisImeUi.ClipboardGrid.elementName,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    LazyVerticalStaggeredGrid(
                        modifier = Modifier.fillMaxSize(),
                        state = gridState,
                        columns = staggeredGridCells,
                    ) {
                        clipboardItems(
                            items = filteredHistory.pinned,
                            key = "pinned-header",
                            title = R.string.clipboard__group_pinned,
                        )
                        clipboardItems(
                            items = filteredHistory.recent,
                            key = "recent-header",
                            title = R.string.clipboard__group_recent,
                        )
                        clipboardItems(
                            items = filteredHistory.other,
                            key = "other-header",
                            title = R.string.clipboard__group_other,
                        )
                    }
                }
            }

            if (popupItem != null) {
                SnyggRow(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures { popupItem = null }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceAround,
                ) {
                    SnyggColumn(modifier = Modifier.weight(0.5f)) {
                        ClipItemView(
                            elementName = FlorisImeUi.ClipboardItemPopup.elementName,
                            modifier = Modifier
                                .widthIn(max = ItemWidth)
                                .weight(1f, fill = false),
                            item = popupItem!!,
                            contentScrollInsteadOfClip = true,
                        )
                        SnyggBox(FlorisImeUi.ClipboardItemTimestamp.elementName) {
                            val formatter = LocalLocalizedDateTimeFormatter.current
                            SnyggText(
                                modifier = Modifier.fillMaxWidth(),
                                text = formatter.format(Instant.ofEpochMilli(popupItem!!.creationTimestampMs)),
                            )
                        }
                    }
                    SnyggColumn(modifier = Modifier.weight(0.5f)) {
                        SnyggColumn(FlorisImeUi.ClipboardItemActions.elementName) {
                            PopupAction(
                                icon = Icons.Outlined.PushPin,
                                text = stringRes(if (popupItem!!.isPinned) {
                                    R.string.clip__unpin_item
                                } else {
                                    R.string.clip__pin_item
                                }),
                            ) {
                                if (popupItem!!.isPinned) {
                                    clipboardManager.unpinClip(popupItem!!)
                                } else {
                                    clipboardManager.pinClip(popupItem!!)
                                }
                                popupItem = null
                            }
                            PopupAction(
                                icon = Icons.Default.Delete,
                                text = stringRes(R.string.clip__delete_item),
                            ) {
                                clipboardManager.deleteClip(popupItem!!, onlyIfUnpinned = false)
                                popupItem = null
                            }
                            PopupAction(
                                icon = Icons.Outlined.ContentPasteGo,
                                text = stringRes(R.string.clip__paste_item),
                            ) {
                                clipboardManager.pasteItem(popupItem!!)
                                popupItem = null
                            }
                            if (popupItem!!.type == ItemType.TEXT) {
                                PopupAction(
                                    icon = Icons.Default.AcUnit,
                                    text = "Save as Snippet",
                                ) {
                                    val text = popupItem!!.stringRepresentation()
                                    val clean = text.trim().take(8).replace("\\s+".toRegex(), "_").lowercase().filter { it.isLetterOrDigit() || it == '_' }
                                    val shortcut = if (clean.isNotBlank()) "!$clean" else "!snip"
                                    context.startActivity(
                                        SnippetDialogActivity.newIntent(
                                            context = context,
                                            id = null,
                                            shortcut = shortcut,
                                            value = text,
                                        )
                                    )
                                    popupItem = null
                                }
                            }
                        }
                    }
                }
            }

            if (showClearAllHistory) {
                SnyggRow(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures { showClearAllHistory = false }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceAround,
                ) {
                    SnyggColumn(
                        elementName = FlorisImeUi.ClipboardClearAllDialog.elementName,
                        modifier = Modifier
                            .width(DialogWidth)
                            .pointerInput(Unit) {
                                detectTapGestures { /* Do nothing */ }
                            },
                    ) {
                        SnyggText(
                            elementName = FlorisImeUi.ClipboardClearAllDialogMessage.elementName,
                            text = stringRes(
                                if (isFilterRowShown) {
                                    R.string.clipboard__confirm_clear_filtered_history__message
                                } else {
                                    R.string.clipboard__confirm_clear_unfiltered_history__message
                                }
                            ),
                        )
                        SnyggRow(FlorisImeUi.ClipboardClearAllDialogButtons.elementName) {
                            Spacer(modifier = Modifier.weight(1f))
                            SnyggButton(
                                elementName = FlorisImeUi.ClipboardClearAllDialogButton.elementName,
                                attributes = mapOf("action" to "no"),
                                onClick = {
                                    showClearAllHistory = false
                                },
                            ) {
                                SnyggText(
                                    text = stringRes(R.string.action__no),
                                )
                            }
                            SnyggButton(
                                elementName = FlorisImeUi.ClipboardClearAllDialogButton.elementName,
                                attributes = mapOf("action" to "yes"),
                                onClick = {
                                    clipboardManager.clearExactHistory(filteredHistory.unpinned)
                                    context.showShortToastSync(R.string.clipboard__cleared_history)
                                    showClearAllHistory = false
                                },
                            ) {
                                SnyggText(
                                    text = stringRes(R.string.action__yes),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun SnippetsMainView() {
        val snippets by SnippetManager.snippetsFlow.collectAsState()
        val editorInstance by context.editorInstance()
        val staggeredGridCells by prefs.clipboard.historyNumGridColumns()
            .observeAsTransformingState { numGridColumns ->
                if (numGridColumns == CLIPBOARD_HISTORY_NUM_GRID_COLUMNS_AUTO) {
                    StaggeredGridCells.Adaptive(160.dp)
                } else {
                    StaggeredGridCells.Fixed(numGridColumns)
                }
            }
        val historyAlpha by animateFloatAsState(targetValue = if (isPopupSurfaceActive()) 0.12f else 1f)

        SnyggBox(
            FlorisImeUi.ClipboardContent.elementName,
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(historyAlpha),
            ) {
                SnyggRow(
                    elementName = FlorisImeUi.ClipboardSubheader.elementName,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ClipCategoryTitle(
                        text = "SNIPPETS",
                        modifier = Modifier.weight(1f),
                    )
                    SnyggButton(
                        elementName = FlorisImeUi.ClipboardHeaderButton.elementName,
                        onClick = {
                            context.startActivity(
                                SnippetDialogActivity.newIntent(context = context)
                            )
                        },
                        modifier = Modifier
                            .height(FlorisImeSizing.smartbarHeight * 0.75f)
                            .padding(vertical = 2.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add",
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            SnyggText(text = "Add")
                        }
                    }
                }

                if (snippets.isEmpty()) {
                    SnyggColumn(
                        elementName = FlorisImeUi.ClipboardContent.elementName,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        SnyggText(text = "No snippets yet")
                        Spacer(modifier = Modifier.height(4.dp))
                        SnyggText(
                            text = "Tap '+ Add' or long-press a copied text clip to save it as a snippet.",
                            modifier = Modifier.alpha(0.7f),
                        )
                    }
                } else {
                    LazyVerticalStaggeredGrid(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 4.dp),
                        columns = staggeredGridCells,
                    ) {
                        items(snippets, key = { it.id }) { snippet ->
                            SnippetCard(
                                snippet = snippet,
                                onClick = {
                                    val expanded = SnippetManager.expand(snippet.value)
                                    editorInstance.commitText(expanded)
                                },
                                onLongClick = {
                                    popupSnippet = snippet
                                },
                            )
                        }
                    }
                }
            }

            if (popupSnippet != null) {
                SnyggRow(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures { popupSnippet = null }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceAround,
                ) {
                    SnyggColumn(modifier = Modifier.weight(0.5f)) {
                        SnippetCard(
                            snippet = popupSnippet!!,
                            onClick = {
                                val expanded = SnippetManager.expand(popupSnippet!!.value)
                                editorInstance.commitText(expanded)
                                popupSnippet = null
                            },
                            onLongClick = { },
                            modifier = Modifier
                                .widthIn(max = ItemWidth)
                                .weight(1f, fill = false),
                        )
                    }
                    SnyggColumn(modifier = Modifier.weight(0.5f)) {
                        SnyggColumn(FlorisImeUi.ClipboardItemActions.elementName) {
                            PopupAction(
                                icon = Icons.Outlined.ContentPasteGo,
                                text = "Paste",
                            ) {
                                val expanded = SnippetManager.expand(popupSnippet!!.value)
                                editorInstance.commitText(expanded)
                                popupSnippet = null
                            }
                            PopupAction(
                                icon = Icons.Default.Edit,
                                text = "Edit",
                            ) {
                                val target = popupSnippet!!
                                popupSnippet = null
                                context.startActivity(
                                    SnippetDialogActivity.newIntent(
                                        context = context,
                                        id = target.id,
                                        shortcut = target.shortcut,
                                        value = target.value,
                                    )
                                )
                            }
                            PopupAction(
                                icon = Icons.Default.Delete,
                                text = "Delete",
                            ) {
                                SnippetManager.delete(popupSnippet!!.id)
                                popupSnippet = null
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun HistoryEmptyView() {
        SnyggColumn(FlorisImeUi.ClipboardContent.elementName,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SnyggText(
                text = stringRes(R.string.clipboard__empty__title),
            )
            SnyggText(
                text = stringRes(R.string.clipboard__empty__message),
            )
        }
    }

    @Composable
    fun HistoryDisabledView() {
        SnyggColumn(FlorisImeUi.ClipboardContent.elementName,
            modifier = Modifier.fillMaxSize(),
        ) {
            SnyggText(
                elementName = FlorisImeUi.ClipboardHistoryDisabledTitle.elementName,
                modifier = Modifier.padding(bottom = 8.dp),
                text = stringRes(R.string.clipboard__disabled__title),
            )
            SnyggText(
                elementName = FlorisImeUi.ClipboardHistoryDisabledMessage.elementName,
                text = stringRes(R.string.clipboard__disabled__message),
            )
            SnyggButton(FlorisImeUi.ClipboardHistoryDisabledButton.elementName,
                onClick = { scope.launch { prefs.clipboard.historyEnabled.set(true) } },
                modifier = Modifier.align(Alignment.End),
            ) {
                SnyggText(
                    text = stringRes(R.string.clipboard__disabled__enable_button),
                )
            }
        }
    }

    @Composable
    fun HistoryLockedView() {
        SnyggColumn(FlorisImeUi.ClipboardContent.elementName,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SnyggText(
                elementName = FlorisImeUi.ClipboardHistoryLockedTitle.elementName,
                text = stringRes(R.string.clipboard__locked__title),
            )
            SnyggText(
                elementName = FlorisImeUi.ClipboardHistoryLockedMessage.elementName,
                text = stringRes(R.string.clipboard__locked__message),
            )
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
    SmartbarSuggestionRowForPanels()
    SnyggColumn(
        modifier = Modifier
            .fillMaxWidth()
            .height(FlorisImeSizing.imeUiHeight()),
    ) {
        HeaderRow()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            if (deviceLocked) {
                HistoryLockedView()
            } else {
                if (isSnippetsTab) {
                    SnippetsMainView()
                } else {
                    if (historyEnabled) {
                        if (filteredHistory.all.isNotEmpty() || !activeFilterTypes.isEmpty()) {
                            HistoryMainView()
                        } else {
                            HistoryEmptyView()
                        }
                    } else {
                        HistoryDisabledView()
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun ClipCategoryTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    SnyggText(FlorisImeUi.ClipboardSubheader.elementName,
        modifier = modifier.fillMaxWidth(),
        text = text.uppercase(),
    )
}

@Composable
private fun ClipTextItemDescription(
    elementName: String,
    attributes: SnyggQueryAttributes,
    text: String,
    modifier: Modifier = Modifier,
): Unit = with(LocalDensity.current) {
    val icon: ImageVector?
    val description: String?
    when {
        NetworkUtils.isEmailAddress(text) -> {
            icon = Icons.Outlined.Email
            description = stringRes(R.string.clipboard__item_description_email)
        }
        NetworkUtils.isUrl(text) -> {
            icon = Icons.Default.Link
            description = stringRes(R.string.clipboard__item_description_url)
        }
        NetworkUtils.isPhoneNumber(text) -> {
            icon = Icons.Default.Phone
            description = stringRes(R.string.clipboard__item_description_phone)
        }
        else -> {
            icon = null
            description = null
        }
    }
    if (icon != null && description != null) {
        SnyggRow(
            elementName = elementName,
            attributes = attributes,
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SnyggIcon(
                imageVector = icon,
            )
            SnyggText(
                modifier = Modifier.weight(1f),
                text = description,
            )
        }
    }
}

@Composable
private fun PopupAction(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    SnyggRow(FlorisImeUi.ClipboardItemAction.elementName,
        modifier = modifier.rippleClickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SnyggIcon(FlorisImeUi.ClipboardItemActionIcon.elementName,
            imageVector = icon,
        )
        SnyggText(FlorisImeUi.ClipboardItemActionText.elementName,
            modifier = Modifier.weight(1f),
            text = text,
        )
    }
}

@Composable
private fun SnippetCard(
    snippet: SnippetItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SnyggBox(
        elementName = FlorisImeUi.ClipboardItem.elementName,
        modifier = modifier.fillMaxWidth(),
        clickAndSemanticsModifier = Modifier.combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(),
            onClick = onClick,
            onLongClick = onLongClick,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
        ) {
            Text(
                text = snippet.shortcut,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = snippet.value,
                fontSize = 12.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (snippet.value.contains("{date}") || snippet.value.contains("{time}") ||
                snippet.value.contains("{datetime}") || snippet.value.contains("{uuid}")) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Dynamic: " + SnippetManager.expand(snippet.value),
                    fontSize = 10.sp,
                    fontStyle = FontStyle.Italic,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.alpha(0.7f),
                )
            }
        }
    }
}
