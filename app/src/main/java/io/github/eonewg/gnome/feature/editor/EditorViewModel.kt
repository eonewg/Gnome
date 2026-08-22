package io.github.eonewg.gnome.feature.editor

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skydoves.sandwich.ApiResponse
import com.skydoves.sandwich.suspendOnSuccess
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.eonewg.gnome.core.model.Attachment
import io.github.eonewg.gnome.core.model.MemoVisibility
import io.github.eonewg.gnome.data.model.Account
import io.github.eonewg.gnome.data.model.ShareContent
import io.github.eonewg.gnome.data.service.AccountService
import io.github.eonewg.gnome.data.service.MemoService
import io.github.eonewg.gnome.ext.settingsDataStore
import io.github.eonewg.gnome.ext.suspendOnErrorMessage
import io.github.eonewg.gnome.ui.page.memoinput.restorableMemoInputDraft
import io.github.eonewg.gnome.util.extractCustomTags
import io.github.eonewg.gnome.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

/**
 * The single editor core shared by every entry point: inline bottom sheet,
 * fullscreen create, fullscreen edit, Quick Settings capture and share
 * intents. Persists through the local-first pipeline — Room transaction →
 * success event → background sync — and never waits for HTTP.
 */
@HiltViewModel
class EditorViewModel @Inject constructor(
    private val memoService: MemoService,
    private val accountService: AccountService,
    @param:ApplicationContext private val appContext: Context,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<EditorEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<EditorEvent> = _events.asSharedFlow()

    private val draft = appContext.settingsDataStore.data.map { settings ->
        settings.usersList.firstOrNull { it.accountKey == settings.currentUser }?.settings?.draft
    }

    /** Tags discovered on the server; merged into the suggestions once. */
    private var remoteTags: Set<String> = emptySet()

    private var shareMode = false

    init {
        viewModelScope.launch {
            accountService.currentAccount.collect { account ->
                _uiState.update { it.copy(isLocalAccount = account is Account.Local) }
            }
        }
        viewModelScope.launch {
            // Local tags stay fresh as the timeline changes; remote accounts also
            // pull the server's tag list once per editor session.
            memoService.domainMemos.collect { memos ->
                val localTags = memos.asSequence()
                    .flatMap { extractCustomTags(it.content).asSequence() }
                    .filter { it.isNotBlank() }
                    .toSet()
                _uiState.update { it.copy(tags = (localTags + remoteTags).sorted()) }
            }
        }
        viewModelScope.launch {
            memoService.getMemoRepository().listTags().suspendOnSuccess {
                remoteTags = data.filter { it.isNotBlank() }.toSet()
                _uiState.update { state ->
                    state.copy(tags = (state.tags.toSet() + remoteTags).sorted())
                }
            }
        }
    }

    /**
     * Loads the initial editor content exactly once: the edit target, the
     * shared payload, or the retained draft. Idempotent so hosts that stay
     * composed (timeline bottom sheet) can call it freely.
     */
    fun start(memoIdentifier: String?, shareContent: ShareContent?, defaultVisibility: MemoVisibility) {
        if (_uiState.value.initialized) return
        shareMode = shareContent != null
        viewModelScope.launch {
            val savedText = savedStateHandle.get<String>(KEY_TEXT)
            val memo = memoIdentifier?.let { memoService.getMemoRepository().getMemo(it) }
            when {
                memo != null -> _uiState.update {
                    it.copy(
                        memoIdentifier = memo.id,
                        text = newEditorText(savedText ?: memo.content),
                        initialContent = memo.content,
                        visibility = memo.visibility,
                        attachments = memo.attachments,
                        initialAttachmentCount = memo.attachments.size,
                    )
                }

                shareContent != null -> {
                    _uiState.update { it.copy(text = newEditorText(shareContent.text), visibility = defaultVisibility) }
                    for (item in shareContent.images) {
                        uploadImages(listOf(item))
                    }
                }

                else -> {
                    val draftText = savedText ?: draft.first()?.let { restorableMemoInputDraft(it) }.orEmpty()
                    _uiState.update { it.copy(text = newEditorText(draftText), visibility = defaultVisibility) }
                }
            }
            _uiState.update { it.copy(initialized = true) }
        }
    }

    fun setText(value: TextFieldValue) {
        savedStateHandle[KEY_TEXT] = value.text
        _uiState.update { it.copy(text = value) }
    }

    fun updateText(transform: (TextFieldValue) -> TextFieldValue) {
        setText(transform(_uiState.value.text))
    }

    fun setVisibility(visibility: MemoVisibility) {
        _uiState.update { it.copy(visibility = visibility) }
    }

    /**
     * Publishes through the local-first pipeline; emits [EditorEvent.Submitted]
     * as soon as the Room transaction committed — never after the server push.
     */
    fun submit() {
        if (_uiState.value.submitting) return
        viewModelScope.launch {
            _uiState.update { it.copy(submitting = true) }
            val state = _uiState.value
            val repository = memoService.getMemoRepository()
            val editTargetId = state.memoIdentifier
            val response = if (editTargetId != null) {
                repository.updateMemo(
                    identifier = editTargetId,
                    content = state.text.text,
                    attachments = state.attachments,
                    visibility = state.visibility,
                )
            } else {
                repository.createMemo(
                    content = state.text.text,
                    visibility = state.visibility,
                    attachments = state.attachments,
                )
            }
            _uiState.update { it.copy(submitting = false) }
            response.suspendOnSuccess {
                WidgetUpdater.updateWidgets(appContext)
                if (!state.isEditMode) {
                    setText(TextFieldValue(""))
                    persistDraft("")
                }
                _events.tryEmit(EditorEvent.Submitted)
            }.suspendOnErrorMessage { message ->
                _events.tryEmit(EditorEvent.ShowMessage(message))
            }
        }
    }

    /** Discards the working text without touching the stored draft. */
    fun discardText() {
        setText(TextFieldValue(""))
    }

    /**
     * Bottom-sheet hosts call this when the editor is hidden; pure tag
     * selections are transient and must not survive as drafts.
     */
    fun onEditorHidden() {
        val state = _uiState.value
        if (state.isEditMode || shareMode) return
        val retainedDraft = restorableMemoInputDraft(state.text.text)
        if (retainedDraft != state.text.text) {
            setText(TextFieldValue(""))
            persistDraft("")
        }
    }

    /** Persists the current text as the account draft; no-op for edit/share. */
    fun persistDraftOnExit() {
        val state = _uiState.value
        if (state.isEditMode || shareMode) return
        persistDraft(restorableMemoInputDraft(state.text.text))
    }

    private fun persistDraft(content: String) {
        viewModelScope.launch(Dispatchers.IO) {
            appContext.settingsDataStore.updateData { settings ->
                val index = settings.usersList.indexOfFirst { it.accountKey == settings.currentUser }
                if (index == -1) {
                    return@updateData settings
                }
                val users = settings.usersList.toMutableList()
                val user = users[index]
                users[index] = user.copy(settings = user.settings.copy(draft = content))
                settings.copy(usersList = users)
            }
        }
    }

    fun uploadImages(uris: List<Uri>) {
        viewModelScope.launch {
            uris.take(MaxSelectableImages).forEach { uri ->
                upload(uri).suspendOnErrorMessage { message ->
                    _events.tryEmit(EditorEvent.ShowMessage(message))
                }
            }
            delay(300)
            _events.tryEmit(EditorEvent.Refocus)
        }
    }

    fun uploadAttachment(uri: Uri) {
        viewModelScope.launch {
            upload(uri).suspendOnErrorMessage { message ->
                _events.tryEmit(EditorEvent.ShowMessage(message))
            }
        }
    }

    private suspend fun upload(uri: Uri): ApiResponse<Attachment> = withContext(Dispatchers.IO) {
        try {
            val mimeType = appContext.contentResolver.getType(uri)
            val extension = mimeType?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
            val filename = queryDisplayName(uri)
                ?: ("attachment_${UUID.randomUUID()}" + if (extension.isNullOrBlank()) "" else ".$extension")

            memoService.getMemoRepository()
                .createAttachment(filename, mimeType, uri, _uiState.value.memoIdentifier)
                .suspendOnSuccess {
                    _uiState.update { state ->
                        state.copy(attachments = state.attachments + data)
                    }
                }
        } catch (e: Exception) {
            ApiResponse.Failure.Exception(e)
        }
    }

    fun deleteAttachment(identifier: String) {
        viewModelScope.launch {
            memoService.getMemoRepository().deleteAttachment(identifier).suspendOnSuccess {
                _uiState.update { state ->
                    state.copy(attachments = state.attachments.filterNot { it.id == identifier })
                }
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) {
                    return@use null
                }
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index == -1) {
                    null
                } else {
                    cursor.getString(index)
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        const val KEY_TEXT = "editor_text"
        const val MaxSelectableImages = 100
    }
}
