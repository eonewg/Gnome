package io.github.eonewg.gnome.ui.page.memoinput

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.activity.result.contract.ActivityResultContracts.TakePicture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.skydoves.sandwich.suspendOnSuccess
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import io.github.eonewg.gnome.GnomeFileProvider
import io.github.eonewg.gnome.data.model.MemoVisibility
import io.github.eonewg.gnome.data.model.ShareContent
import io.github.eonewg.gnome.ext.popBackStackIfLifecycleIsResumed
import io.github.eonewg.gnome.ext.suspendOnErrorMessage
import io.github.eonewg.gnome.ui.page.common.LocalRootNavController
import io.github.eonewg.gnome.ui.theme.GnomeDesign
import io.github.eonewg.gnome.ui.util.PickMultipleImagesContract
import io.github.eonewg.gnome.util.extractCustomTags
import io.github.eonewg.gnome.viewmodel.LocalMemos
import io.github.eonewg.gnome.viewmodel.LocalUserState
import io.github.eonewg.gnome.viewmodel.MemoInputViewModel

private const val MaxSelectableImages = 100

enum class MemoInputPresentation {
    FullScreen,
    BottomSheet,
}

@Composable
fun MemoInputPage(
    viewModel: MemoInputViewModel = hiltViewModel(),
    memoIdentifier: String? = null,
    shareContent: ShareContent? = null,
    onFinished: (() -> Unit)? = null,
    presentation: MemoInputPresentation = MemoInputPresentation.FullScreen,
    active: Boolean = true,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarState = remember { SnackbarHostState() }
    val navController = LocalRootNavController.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val memosViewModel = LocalMemos.current
    val userStateViewModel = LocalUserState.current
    val currentAccount by userStateViewModel.currentAccount.collectAsState()
    val colors = GnomeDesign.colors
    val isBottomSheet = presentation == MemoInputPresentation.BottomSheet
    val memo = remember { memosViewModel.memos.toList().find { it.identifier == memoIdentifier } }
    var initialContent by remember { mutableStateOf(memo?.content ?: "") }
    var text by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(memo?.content ?: "", TextRange(memo?.content?.length ?: 0)))
    }
    var visibilityMenuExpanded by remember { mutableStateOf(false) }
    var photoImageUri by remember { mutableStateOf<Uri?>(null) }
    var showExitConfirmation by remember { mutableStateOf(false) }
    var wasActive by remember { mutableStateOf(active) }

    val activeHashtag = remember(text.text, text.selection) { findActiveHashtag(text) }
    val tagSuggestions = remember(active, isBottomSheet, activeHashtag, memosViewModel.tags) {
        if (isBottomSheet && !active) {
            emptyList()
        } else {
            hashtagSuggestions(activeHashtag, memosViewModel.tags)
        }
    }

    val defaultVisibility = userStateViewModel.currentUser?.defaultVisibility ?: MemoVisibility.PRIVATE
    var currentVisibility by remember { mutableStateOf(memo?.visibility ?: defaultVisibility) }

    val validMimeTypePrefixes = remember {
        setOf("text/")
    }

    fun submit() = coroutineScope.launch {
        val tags = extractCustomTags(text.text)

        memo?.let {
            viewModel.editMemo(memo.identifier, text.text, currentVisibility, tags.toList()).suspendOnSuccess {
                memosViewModel.refreshLocalSnapshot()
                onFinished?.invoke() ?: navController.popBackStack()
            }.suspendOnErrorMessage { message ->
                snackbarState.showSnackbar(message)
            }
            return@launch
        }

        viewModel.createMemo(text.text, currentVisibility, tags.toList()).suspendOnSuccess {
            text = TextFieldValue("")
            viewModel.updateDraft("")
            memosViewModel.refreshLocalSnapshot()
            onFinished?.invoke() ?: navController.popBackStack()
        }.suspendOnErrorMessage { message ->
            snackbarState.showSnackbar(message)
        }
    }

    fun handleExit() {
        if (text.text != initialContent || viewModel.uploadResources.size != (memo?.resources?.size ?: 0)) {
            showExitConfirmation = true
        } else {
            onFinished?.invoke()
                ?: navController.popBackStackIfLifecycleIsResumed(lifecycleOwner)
        }
    }

    fun uploadImages(uris: List<Uri>) = coroutineScope.launch {
        uris.take(MaxSelectableImages).forEach { uri ->
            viewModel.upload(uri, memo?.identifier).suspendOnErrorMessage { message ->
                snackbarState.showSnackbar(message)
            }
        }
        delay(300)
        focusRequester.requestFocus()
    }

    fun uploadImage(uri: Uri) {
        uploadImages(listOf(uri))
    }

    val pickImages = rememberLauncherForActivityResult(
        PickMultipleImagesContract(MaxSelectableImages)
    ) { uris ->
        if (uris.isNotEmpty()) {
            uploadImages(uris)
        }
    }

    val takePhoto = rememberLauncherForActivityResult(TakePicture()) { success ->
        if (success) {
            photoImageUri?.let { uploadImage(it) }
        }
    }

    val pickAttachment = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        uri?.let {
            coroutineScope.launch {
                viewModel.upload(it, memo?.identifier).suspendOnErrorMessage { message ->
                    snackbarState.showSnackbar(message)
                }
            }
        }
    }

    BackHandler(enabled = !isBottomSheet) {
        handleExit()
    }

    if (isBottomSheet) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(BottomSheetEditorHeight)
        ) {
            Column(modifier = Modifier.matchParentSize()) {
                MemoInputEditor(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    text = text,
                    onTextChange = { updated ->
                        val handled = if (
                            text.text != updated.text &&
                            updated.selection.start == updated.selection.end &&
                            updated.text.length == text.text.length + 1 &&
                            updated.selection.start > 0 &&
                            updated.text[updated.selection.start - 1] == '\n'
                        ) {
                            handleEnterInText(text)
                        } else {
                            null
                        }
                        text = handled ?: updated
                    },
                    focusRequester = focusRequester,
                    validMimeTypePrefixes = validMimeTypePrefixes,
                    onDroppedText = { droppedText ->
                        text = text.copy(text = text.text + droppedText)
                    },
                    uploadResources = viewModel.uploadResources.toList(),
                    inputViewModel = viewModel,
                    tagSuggestions = tagSuggestions,
                    compactTagSuggestions = true,
                    onTagSuggestionSelected = { tag ->
                        activeHashtag?.let { token ->
                            text = replaceActiveHashtag(text, token, tag)
                            focusRequester.requestFocus()
                        }
                    },
                )
                MemoInputBottomBar(
                    currentAccount = currentAccount,
                    currentVisibility = currentVisibility,
                    visibilityMenuExpanded = visibilityMenuExpanded,
                    onVisibilityExpandedChange = { visibilityMenuExpanded = it },
                    onVisibilitySelected = { currentVisibility = it },
                    onHashTagClick = {
                        text = replaceSelection(text, "#")
                        focusRequester.requestFocus()
                        keyboardController?.show()
                    },
                    onToggleTodoItem = {
                        text = toggleTodoItemInText(text)
                    },
                    onPickImage = {
                        pickImages.launch(Unit)
                    },
                    onPickAttachment = {
                        pickAttachment.launch(arrayOf("*/*"))
                    },
                    onTakePhoto = {
                        try {
                            val uri = GnomeFileProvider.getImageUri(navController.context)
                            photoImageUri = uri
                            takePhoto.launch(uri)
                        } catch (e: ActivityNotFoundException) {
                            coroutineScope.launch {
                                snackbarState.showSnackbar(e.localizedMessage ?: "Unable to take picture.")
                            }
                        }
                    },
                    onFormat = { format ->
                        text = applyMarkdownFormatToText(text, format)
                    },
                    canSubmit = text.text.isNotEmpty() || viewModel.uploadResources.isNotEmpty(),
                    onSubmit = { submit() },
                )
            }
            SnackbarHost(
                hostState = snackbarState,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    } else Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = colors.cardBackground,
        topBar = {
            if (!isBottomSheet) {
                MemoInputTopBar(
                    isEditMode = memo != null,
                    canSubmit = text.text.isNotEmpty() || viewModel.uploadResources.isNotEmpty(),
                    onClose = { handleExit() },
                    onSubmit = { submit() }
                )
            }
        },
        bottomBar = {
            MemoInputBottomBar(
                currentAccount = currentAccount,
                currentVisibility = currentVisibility,
                visibilityMenuExpanded = visibilityMenuExpanded,
                onVisibilityExpandedChange = { visibilityMenuExpanded = it },
                onVisibilitySelected = { currentVisibility = it },
                onHashTagClick = {
                    text = replaceSelection(text, "#")
                    focusRequester.requestFocus()
                    keyboardController?.show()
                },
                onToggleTodoItem = {
                    text = toggleTodoItemInText(text)
                },
                onPickImage = {
                    pickImages.launch(Unit)
                },
                onPickAttachment = {
                    pickAttachment.launch(arrayOf("*/*"))
                },
                onTakePhoto = {
                    try {
                        val uri = GnomeFileProvider.getImageUri(navController.context)
                        photoImageUri = uri
                        takePhoto.launch(uri)
                    } catch (e: ActivityNotFoundException) {
                        coroutineScope.launch {
                            snackbarState.showSnackbar(e.localizedMessage ?: "Unable to take picture.")
                        }
                    }
                },
                onFormat = { format ->
                    text = applyMarkdownFormatToText(text, format)
                },
                canSubmit = text.text.isNotEmpty() || viewModel.uploadResources.isNotEmpty(),
                onSubmit = if (isBottomSheet) {
                    { submit() }
                } else {
                    null
                },
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarState)
        }
    ) { innerPadding ->
        MemoInputEditor(
            modifier = Modifier.padding(innerPadding),
            text = text,
            onTextChange = { updated ->
                if (
                    text.text != updated.text &&
                    updated.selection.start == updated.selection.end &&
                    updated.text.length == text.text.length + 1 &&
                    updated.selection.start > 0 &&
                    updated.text[updated.selection.start - 1] == '\n'
                ) {
                    val handled = handleEnterInText(text)
                    if (handled != null) {
                        text = handled
                        return@MemoInputEditor
                    }
                }
                text = updated
            },
            focusRequester = focusRequester,
            validMimeTypePrefixes = validMimeTypePrefixes,
            onDroppedText = { droppedText ->
                text = text.copy(text = text.text + droppedText)
            },
            uploadResources = viewModel.uploadResources.toList(),
            inputViewModel = viewModel,
            tagSuggestions = tagSuggestions,
            compactTagSuggestions = isBottomSheet,
            onTagSuggestionSelected = { tag ->
                activeHashtag?.let { token ->
                    text = replaceActiveHashtag(text, token, tag)
                    focusRequester.requestFocus()
                }
            },
        )
    }

    if (showExitConfirmation) {
        SaveChangesDialog(
            onSave = {
                showExitConfirmation = false
                submit()
            },
            onDiscard = {
                showExitConfirmation = false
                text = TextFieldValue("")
                onFinished?.invoke()
                    ?: navController.popBackStackIfLifecycleIsResumed(lifecycleOwner)
            },
            onDismiss = {
                showExitConfirmation = false
            }
        )
    }

    LaunchedEffect(active, focusRequester, keyboardController) {
        if (active) {
            focusRequester.requestFocus()
            keyboardController?.show()
        } else if (isBottomSheet && wasActive) {
            val retainedDraft = restorableMemoInputDraft(text.text)
            if (retainedDraft != text.text) {
                text = TextFieldValue("")
                viewModel.updateDraft("")
            }
        }
        wasActive = active
    }

    LaunchedEffect(Unit) {
        if (!isBottomSheet) {
            memosViewModel.loadTags()
        }
        viewModel.uploadResources.clear()
        when {
            memo != null -> {
                viewModel.uploadResources.addAll(memo.resources)
                initialContent = memo.content
            }

            shareContent != null -> {
                text = TextFieldValue(shareContent.text, TextRange(shareContent.text.length))
                for (item in shareContent.images) {
                    uploadImage(item)
                }
            }

            else -> {
                viewModel.draft.first()?.let { draft ->
                    val retainedDraft = restorableMemoInputDraft(draft)
                    text = TextFieldValue(
                        retainedDraft,
                        TextRange(retainedDraft.length),
                    )
                    if (retainedDraft != draft) {
                        viewModel.updateDraft("")
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (memo == null && shareContent == null) {
                viewModel.updateDraft(restorableMemoInputDraft(text.text))
            }
        }
    }
}

private val BottomSheetEditorHeight = 160.dp
