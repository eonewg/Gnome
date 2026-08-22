package io.github.eonewg.gnome.feature.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.material3.SnackbarHostState
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.eonewg.gnome.core.model.MemoVisibility
import io.github.eonewg.gnome.core.model.toCore
import io.github.eonewg.gnome.data.model.ShareContent
import io.github.eonewg.gnome.feature.account.AccountSessionViewModel

/**
 * Host wiring for the shared editor core. Used by the timeline bottom sheet,
 * fullscreen create/edit destinations, the share-intent flow and the Quick
 * Settings capture activity — one [EditorViewModel], one publish path.
 */
@Composable
fun EditorRoute(
    memoIdentifier: String? = null,
    shareContent: ShareContent? = null,
    onFinished: () -> Unit = {},
    presentation: EditorPresentation = EditorPresentation.FullScreen,
    active: Boolean = true,
) {
    val viewModel: EditorViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val accountSessionViewModel: AccountSessionViewModel = hiltViewModel()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val snackbarState = remember { SnackbarHostState() }

    fun exitEditor() {
        onFinished()
    }

    LaunchedEffect(Unit) {
        val defaultVisibility =
            accountSessionViewModel.currentAccount.value?.toUser()?.defaultVisibility?.toCore()
                ?: MemoVisibility.PRIVATE
        viewModel.start(memoIdentifier, shareContent, defaultVisibility)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                EditorEvent.Submitted -> onFinished()
                EditorEvent.Refocus -> focusRequester.requestFocus()
                is EditorEvent.ShowMessage -> snackbarState.showSnackbar(event.message)
            }
        }
    }

    var wasActive by remember { mutableStateOf(active) }
    LaunchedEffect(active) {
        if (active) {
            focusRequester.requestFocus()
            keyboardController?.show()
        } else if (wasActive) {
            viewModel.onEditorHidden()
        }
        wasActive = active
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.persistDraftOnExit()
        }
    }

    EditorScreen(
        uiState = uiState,
        presentation = presentation,
        active = active,
        snackbarHostState = snackbarState,
        focusRequester = focusRequester,
        onTextChange = viewModel::setText,
        onUpdateText = viewModel::updateText,
        onVisibilitySelected = viewModel::setVisibility,
        onSubmit = viewModel::submit,
        onExit = { exitEditor() },
        onDiscardAndExit = {
            viewModel.discardText()
            exitEditor()
        },
        onUploadImages = viewModel::uploadImages,
        onUploadAttachment = viewModel::uploadAttachment,
        onDeleteAttachment = viewModel::deleteAttachment,
    )
}
