package io.github.eonewg.gnome.ui.page.memoinput

import android.content.ClipData
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Attachment
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.FormatStrikethrough
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import io.github.eonewg.gnome.R
import io.github.eonewg.gnome.data.local.entity.ResourceEntity
import io.github.eonewg.gnome.data.model.Account
import io.github.eonewg.gnome.data.model.MemoVisibility
import io.github.eonewg.gnome.ext.icon
import io.github.eonewg.gnome.ext.string
import io.github.eonewg.gnome.ext.titleResource
import io.github.eonewg.gnome.ui.component.Attachment
import io.github.eonewg.gnome.ui.component.InputImage
import io.github.eonewg.gnome.ui.theme.GnomeDesign
import io.github.eonewg.gnome.util.findCustomTagMatches
import io.github.eonewg.gnome.viewmodel.MemoInputViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MemoInputTopBar(
    isEditMode: Boolean,
    canSubmit: Boolean,
    onClose: () -> Unit,
    onSubmit: () -> Unit
) {
    val colors = GnomeDesign.colors
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = colors.cardBackground,
            scrolledContainerColor = colors.cardBackground,
        ),
        title = {},
        navigationIcon = {
            TextButton(
                onClick = onClose,
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                Text(
                    text = R.string.cancel.string,
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        },
        actions = {
            TextButton(
                enabled = canSubmit,
                onClick = onSubmit,
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                Text(
                    text = R.string.post.string,
                    color = if (canSubmit) colors.accent else colors.textSecondary.copy(alpha = 0.45f),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    )
}

@Composable
private fun FormattingButtons(
    onFormat: (MarkdownFormat) -> Unit,
) {
    MarkdownFormat.entries.forEach { format ->
        IconButton(onClick = { onFormat(format) }) {
            when (format) {
                MarkdownFormat.BOLD -> Icon(Icons.Outlined.FormatBold, contentDescription = format.label)
                MarkdownFormat.ITALIC -> Icon(Icons.Outlined.FormatItalic, contentDescription = format.label)
                MarkdownFormat.STRIKETHROUGH -> Icon(Icons.Outlined.FormatStrikethrough, contentDescription = format.label)
                MarkdownFormat.BULLET -> Icon(Icons.AutoMirrored.Outlined.FormatListBulleted, contentDescription = format.label)
                MarkdownFormat.NUMBERED -> Icon(Icons.Outlined.FormatListNumbered, contentDescription = format.label)
                MarkdownFormat.H1, MarkdownFormat.H2, MarkdownFormat.H3 -> Text(
                    text = format.label,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
internal fun MemoInputBottomBar(
    currentAccount: Account?,
    currentVisibility: MemoVisibility,
    visibilityMenuExpanded: Boolean,
    onVisibilityExpandedChange: (Boolean) -> Unit,
    onVisibilitySelected: (MemoVisibility) -> Unit,
    onHashTagClick: () -> Unit,
    onToggleTodoItem: () -> Unit,
    onPickImage: () -> Unit,
    onPickAttachment: () -> Unit,
    onTakePhoto: () -> Unit,
    onFormat: (MarkdownFormat) -> Unit,
    canSubmit: Boolean = false,
    onSubmit: (() -> Unit)? = null,
) {
    val scrollState = rememberScrollState()
    val colors = GnomeDesign.colors
    val isCompactBottomSheet = onSubmit != null

    BottomAppBar(
        modifier = if (isCompactBottomSheet) Modifier.height(56.dp) else Modifier,
        containerColor = colors.cardBackground,
        contentColor = colors.textPrimary,
        tonalElevation = 0.dp,
        contentPadding = PaddingValues(horizontal = 8.dp),
        windowInsets = if (isCompactBottomSheet) {
            WindowInsets(0, 0, 0, 0)
        } else {
            BottomAppBarDefaults.windowInsets
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(scrollState),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onHashTagClick) {
                    Text(
                        text = "#",
                        style = MaterialTheme.typography.headlineSmall,
                        color = colors.textPrimary,
                    )
                }

                if (currentAccount !is Account.Local) {
                    Box {
                        DropdownMenu(
                            expanded = visibilityMenuExpanded,
                            onDismissRequest = { onVisibilityExpandedChange(false) },
                            properties = PopupProperties(focusable = false)
                        ) {
                            enumValues<MemoVisibility>().forEach { visibility ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(visibility.titleResource)) },
                                    onClick = {
                                        onVisibilitySelected(visibility)
                                        onVisibilityExpandedChange(false)
                                    },
                                    leadingIcon = {
                                        Icon(
                                            visibility.icon,
                                            contentDescription = stringResource(visibility.titleResource)
                                        )
                                    },
                                    trailingIcon = {
                                        if (currentVisibility == visibility) {
                                            Icon(Icons.Outlined.Check, contentDescription = null)
                                        }
                                    }
                                )
                            }
                        }
                        IconButton(onClick = { onVisibilityExpandedChange(!visibilityMenuExpanded) }) {
                            Icon(
                                currentVisibility.icon,
                                contentDescription = stringResource(currentVisibility.titleResource)
                            )
                        }
                    }
                }

                IconButton(onClick = onToggleTodoItem) {
                    Icon(Icons.Outlined.CheckBox, contentDescription = stringResource(R.string.add_task))
                }

                IconButton(onClick = onPickImage) {
                    Icon(Icons.Outlined.Image, contentDescription = stringResource(R.string.add_image))
                }

                IconButton(onClick = onPickAttachment) {
                    Icon(Icons.Outlined.Attachment, contentDescription = stringResource(R.string.attachment))
                }

                IconButton(onClick = onTakePhoto) {
                    Icon(Icons.Outlined.PhotoCamera, contentDescription = stringResource(R.string.take_photo))
                }

                Spacer(modifier = Modifier.size(4.dp))

                FormattingButtons(onFormat = onFormat)
            }

            if (onSubmit != null) {
                Surface(
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .size(width = 56.dp, height = 42.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = if (canSubmit) colors.accent else colors.subtleSurface,
                    contentColor = if (canSubmit) colors.cardBackground else colors.textSecondary.copy(alpha = 0.35f),
                ) {
                    IconButton(
                        enabled = canSubmit,
                        onClick = onSubmit,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = R.string.post.string,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MemoInputEditor(
    modifier: Modifier = Modifier,
    text: TextFieldValue,
    onTextChange: (TextFieldValue) -> Unit,
    focusRequester: FocusRequester,
    validMimeTypePrefixes: Set<String>,
    onDroppedText: (String) -> Unit,
    uploadResources: List<ResourceEntity>,
    inputViewModel: MemoInputViewModel,
    tagSuggestions: List<String>,
    compactTagSuggestions: Boolean = false,
    onTagSuggestionSelected: (String) -> Unit,
) {
    val colors = GnomeDesign.colors
    val tagVisualTransformation = remember(colors.tagForeground) {
        VisualTransformation { source ->
            val highlighted = buildAnnotatedString {
                append(source)
                findCustomTagMatches(source.text).forEach { match ->
                    addStyle(
                        style = SpanStyle(color = colors.tagForeground),
                        start = match.range.first,
                        end = match.range.last + 1,
                    )
                }
            }
            TransformedText(highlighted, OffsetMapping.Identity)
        }
    }
    val imageResources = remember(uploadResources) {
        uploadResources.filter { it.mimeType?.startsWith("image/") == true }
    }
    val attachmentResources = remember(uploadResources) {
        uploadResources.filterNot { it.mimeType?.startsWith("image/") == true }
    }

    Column(
        modifier
            .fillMaxHeight()
            .dragAndDropTarget(
                shouldStartDragAndDrop = accept@{ startEvent ->
                    startEvent
                        .mimeTypes()
                        .any { eventMimeType ->
                            validMimeTypePrefixes.any(eventMimeType::startsWith)
                        }
                },
                target = object : DragAndDropTarget {
                    override fun onDrop(event: DragAndDropEvent): Boolean {
                        val androidDragEvent = event.toAndroidDragEvent()
                        val concatText = androidDragEvent.clipData
                            .textList()
                            .fold("") { acc, droppedText ->
                                if (acc.isNotBlank()) {
                                    acc.trimEnd { it == '\n' } + "\n\n" + droppedText.trimStart { it == '\n' }
                                } else {
                                    droppedText
                                }
                            }
                        onDroppedText(concatText)
                        return true
                    }
                }
            )
    ) {
        BasicTextField(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 20.dp,
                    top = if (compactTagSuggestions) 0.dp else 12.dp,
                    end = 20.dp,
                    bottom = if (compactTagSuggestions) 4.dp else 20.dp,
                )
                .weight(1f)
                .focusRequester(focusRequester),
            value = text,
            onValueChange = onTextChange,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.textPrimary),
            visualTransformation = tagVisualTransformation,
            cursorBrush = SolidColor(colors.accent),
            decorationBox = { innerTextField ->
                Box {
                    if (text.text.isEmpty()) {
                        Text(
                            text = R.string.any_thoughts.string,
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.textSecondary,
                        )
                    }
                    innerTextField()
                }
            },
        )

        if (tagSuggestions.isNotEmpty() && !compactTagSuggestions) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 20.dp,
                        end = 20.dp,
                        bottom = 8.dp,
                    ),
                shape = MaterialTheme.shapes.large,
                color = colors.subtleSurface,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                    items(tagSuggestions, key = { it }) { tag ->
                        DropdownMenuItem(
                            text = { Text("#$tag") },
                            onClick = { onTagSuggestionSelected(tag) },
                            leadingIcon = {
                                Icon(Icons.Outlined.Tag, contentDescription = null)
                            },
                            colors = MenuDefaults.itemColors(
                                textColor = colors.tagForeground,
                                leadingIconColor = colors.tagForeground,
                            ),
                        )
                    }
                }
            }
        }

        if (tagSuggestions.isNotEmpty() && compactTagSuggestions) {
            CompactTagSuggestionPopup(
                tags = tagSuggestions,
                onTagSelected = onTagSuggestionSelected,
            )
        }

        if (imageResources.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .height(80.dp)
                    .padding(
                        start = 15.dp,
                        end = 15.dp,
                        bottom = if (attachmentResources.isEmpty()) 15.dp else 8.dp
                    ),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(imageResources, key = { it.identifier }) { resource ->
                    InputImage(resource = resource, inputViewModel = inputViewModel)
                }
            }
        }

        if (attachmentResources.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .padding(start = 15.dp, end = 15.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(attachmentResources, key = { it.identifier }) { resource ->
                    Attachment(
                        resource = resource,
                        onRemove = { inputViewModel.deleteResource(resource.identifier) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactTagSuggestionPopup(
    tags: List<String>,
    onTagSelected: (String) -> Unit,
) {
    val colors = GnomeDesign.colors
    val density = LocalDensity.current
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val gapPx = with(density) { 8.dp.roundToPx() }
    val positionProvider = remember(gapPx) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset {
                return IntOffset(
                    x = ((windowSize.width - popupContentSize.width) / 2).coerceAtLeast(0),
                    y = (anchorBounds.top - popupContentSize.height - gapPx).coerceAtLeast(gapPx),
                )
            }
        }
    }

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = {},
        properties = PopupProperties(
            focusable = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            clippingEnabled = true,
        ),
    ) {
        Surface(
            modifier = Modifier
                .width(screenWidth - 44.dp)
                .heightIn(max = 260.dp),
            shape = RoundedCornerShape(16.dp),
            color = colors.cardBackground,
            contentColor = colors.textPrimary,
            tonalElevation = 0.dp,
            shadowElevation = 10.dp,
        ) {
            LazyColumn {
                items(tags, key = { it }) { tag ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTagSelected(tag) }
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "# $tag",
                            color = colors.textPrimary,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SaveChangesDialog(
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save Changes?") },
        text = { Text("Do you want to save changes before exiting?") },
        confirmButton = {
            Button(onClick = onSave) {
                Text("Save")
            }
        },
        dismissButton = {
            Button(onClick = onDiscard) {
                Text("Discard")
            }
        }
    )
}

private fun ClipData.textList(): List<String> {
    return (0 until itemCount)
        .mapNotNull(::getItemAt)
        .mapNotNull { it.text?.toString() }
}
