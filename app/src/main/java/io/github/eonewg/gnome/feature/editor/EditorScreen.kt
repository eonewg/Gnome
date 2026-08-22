package io.github.eonewg.gnome.feature.editor

import android.content.ActivityNotFoundException
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.activity.result.contract.ActivityResultContracts.TakePicture
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import io.github.eonewg.gnome.GnomeFileProvider
import io.github.eonewg.gnome.core.model.MemoVisibility
import io.github.eonewg.gnome.core.model.toCore
import io.github.eonewg.gnome.core.model.toData
import io.github.eonewg.gnome.ui.page.memoinput.MemoInputBottomBar
import io.github.eonewg.gnome.ui.page.memoinput.MemoInputEditor
import io.github.eonewg.gnome.ui.page.memoinput.MemoInputTopBar
import io.github.eonewg.gnome.ui.page.memoinput.SaveChangesDialog
import io.github.eonewg.gnome.ui.page.memoinput.applyMarkdownFormatToText
import io.github.eonewg.gnome.ui.page.memoinput.findActiveHashtag
import io.github.eonewg.gnome.ui.page.memoinput.handleEnterInText
import io.github.eonewg.gnome.ui.page.memoinput.hashtagSuggestions
import io.github.eonewg.gnome.ui.page.memoinput.replaceActiveHashtag
import io.github.eonewg.gnome.ui.page.memoinput.replaceSelection
import io.github.eonewg.gnome.ui.page.memoinput.toggleTodoItemInText
import io.github.eonewg.gnome.ui.theme.GnomeDesign
import io.github.eonewg.gnome.ui.util.PickMultipleImagesContract
import kotlinx.coroutines.launch

/**
 * Stateful visuals of the editor for both presentations. Renders
 * [EditorUiState] and forwards interactions upward; owns only transient
 * menu/dialog/launcher state.
 */
@Composable
fun EditorScreen(
    uiState: EditorUiState,
    presentation: EditorPresentation,
    active: Boolean,
    snackbarHostState: SnackbarHostState,
    focusRequester: FocusRequester,
    onTextChange: (TextFieldValue) -> Unit,
    onUpdateText: ((TextFieldValue) -> TextFieldValue) -> Unit,
    onVisibilitySelected: (MemoVisibility) -> Unit,
    onSubmit: () -> Unit,
    onExit: () -> Unit,
    onDiscardAndExit: () -> Unit,
    onUploadImages: (List<Uri>) -> Unit,
    onUploadAttachment: (Uri) -> Unit,
    onDeleteAttachment: (String) -> Unit,
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    val colors = GnomeDesign.colors
    val isBottomSheet = presentation == EditorPresentation.BottomSheet

    var visibilityMenuExpanded by remember { mutableStateOf(false) }
    var photoImageUri by remember { mutableStateOf<Uri?>(null) }
    var showExitConfirmation by remember { mutableStateOf(false) }

    val activeHashtag = remember(uiState.text.text, uiState.text.selection) { findActiveHashtag(uiState.text) }
    val tagSuggestions = remember(active, isBottomSheet, activeHashtag, uiState.tags) {
        if (isBottomSheet && !active) {
            emptyList()
        } else {
            hashtagSuggestions(activeHashtag, uiState.tags)
        }
    }

    val validMimeTypePrefixes = remember {
        setOf("text/")
    }

    fun handleExit() {
        if (uiState.hasUnsavedChanges) {
            showExitConfirmation = true
        } else {
            onExit()
        }
    }

    val handleTextChange: (TextFieldValue) -> Unit = { updated ->
        val current = uiState.text
        val handled = if (
            current.text != updated.text &&
            updated.selection.start == updated.selection.end &&
            updated.text.length == current.text.length + 1 &&
            updated.selection.start > 0 &&
            updated.text[updated.selection.start - 1] == '\n'
        ) {
            handleEnterInText(current)
        } else {
            null
        }
        onTextChange(handled ?: updated)
    }

    val pickImages = rememberLauncherForActivityResult(
        PickMultipleImagesContract(MaxSelectableImages)
    ) { uris ->
        if (uris.isNotEmpty()) {
            onUploadImages(uris)
        }
    }

    val takePhoto = rememberLauncherForActivityResult(TakePicture()) { success ->
        if (success) {
            photoImageUri?.let { onUploadImages(listOf(it)) }
        }
    }

    val pickAttachment = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        uri?.let(onUploadAttachment)
    }

    BackHandler(enabled = !isBottomSheet) {
        handleExit()
    }

    val editor: @Composable (Modifier) -> Unit = { modifier ->
        MemoInputEditor(
            modifier = modifier,
            text = uiState.text,
            onTextChange = handleTextChange,
            focusRequester = focusRequester,
            validMimeTypePrefixes = validMimeTypePrefixes,
            onDroppedText = { droppedText ->
                onUpdateText { it.copy(text = it.text + droppedText) }
            },
            attachments = uiState.attachments,
            onDeleteAttachment = onDeleteAttachment,
            tagSuggestions = tagSuggestions,
            compactTagSuggestions = isBottomSheet,
            onTagSuggestionSelected = { tag ->
                activeHashtag?.let { token ->
                    onUpdateText { replaceActiveHashtag(it, token, tag) }
                    focusRequester.requestFocus()
                }
            },
        )
    }

    val bottomBar: @Composable (submit: (() -> Unit)?) -> Unit = { submit ->
        MemoInputBottomBar(
            isLocalAccount = uiState.isLocalAccount,
            currentVisibility = uiState.visibility.toData(),
            visibilityMenuExpanded = visibilityMenuExpanded,
            onVisibilityExpandedChange = { visibilityMenuExpanded = it },
            onVisibilitySelected = { onVisibilitySelected(it.toCore()) },
            onHashTagClick = {
                onUpdateText { replaceSelection(it, "#") }
                focusRequester.requestFocus()
                keyboardController?.show()
            },
            onToggleTodoItem = {
                onUpdateText { toggleTodoItemInText(it) }
            },
            onPickImage = {
                pickImages.launch(Unit)
            },
            onPickAttachment = {
                pickAttachment.launch(arrayOf("*/*"))
            },
            onTakePhoto = {
                try {
                    val uri = GnomeFileProvider.getImageUri(context)
                    photoImageUri = uri
                    takePhoto.launch(uri)
                } catch (e: ActivityNotFoundException) {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(e.localizedMessage ?: "Unable to take picture.")
                    }
                }
            },
            onFormat = { format ->
                onUpdateText { applyMarkdownFormatToText(it, format) }
            },
            canSubmit = uiState.canSubmit,
            onSubmit = submit,
        )
    }

    if (isBottomSheet) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(BottomSheetEditorHeight)
        ) {
            Column(modifier = Modifier.matchParentSize()) {
                editor(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
                bottomBar({ onSubmit() })
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    } else Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = colors.cardBackground,
        topBar = {
            MemoInputTopBar(
                isEditMode = uiState.isEditMode,
                canSubmit = uiState.canSubmit,
                onClose = { handleExit() },
                onSubmit = { onSubmit() }
            )
        },
        bottomBar = {
            bottomBar(null)
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        }
    ) { innerPadding ->
        editor(Modifier.padding(innerPadding))
    }

    if (showExitConfirmation) {
        SaveChangesDialog(
            onSave = {
                showExitConfirmation = false
                onSubmit()
            },
            onDiscard = {
                showExitConfirmation = false
                onDiscardAndExit()
            },
            onDismiss = {
                showExitConfirmation = false
            }
        )
    }
}

private const val MaxSelectableImages = 100
private val BottomSheetEditorHeight = 160.dp
