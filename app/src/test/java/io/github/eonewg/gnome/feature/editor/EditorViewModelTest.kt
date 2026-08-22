package io.github.eonewg.gnome.feature.editor

import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.SavedStateHandle
import io.github.eonewg.gnome.core.model.Attachment
import io.github.eonewg.gnome.core.model.Memo
import io.github.eonewg.gnome.core.model.MemoVisibility
import io.github.eonewg.gnome.data.model.ShareContent
import io.github.eonewg.gnome.feature.FakeAccountService
import io.github.eonewg.gnome.feature.FakeMemoRepository
import io.github.eonewg.gnome.feature.FakeMemoService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * Behavior tests for the Phase 12 editor core: the three start branches
 * (edit / share / create-with-saved-text), idempotent initialization, submit
 * semantics against the domain repository fake, and the draft discard rules.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EditorViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var accountService: FakeAccountService
    private lateinit var repository: FakeMemoRepository
    private lateinit var memoService: FakeMemoService

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        accountService = FakeAccountService.build(RuntimeEnvironment.getApplication())
        repository = FakeMemoRepository()
        memoService = FakeMemoService(accountService, repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()) =
        EditorViewModel(
            memoService = memoService,
            accountService = accountService,
            appContext = RuntimeEnvironment.getApplication(),
            savedStateHandle = savedStateHandle,
        )

    private fun editTarget(): Memo = Memo(
        id = "m1",
        content = "hello world",
        date = Instant.EPOCH,
        visibility = MemoVisibility.PROTECTED,
        attachments = listOf(
            Attachment(id = "att1", filename = "f.png", uri = "content://att/1"),
        ),
    )

    private fun collectEvents(viewModel: EditorViewModel): StateFlow<List<EditorEvent>> {
        val received = MutableStateFlow(emptyList<EditorEvent>())
        viewModel.events
            .onEach { received.value = received.value + it }
            .launchIn(CoroutineScope(testDispatcher))
        return received
    }

    @Test
    fun `start edit branch loads the memo, its visibility and attachments`() = runTest(testDispatcher) {
        repository.memosById["m1"] = editTarget()
        val viewModel = newViewModel()

        viewModel.start("m1", shareContent = null, defaultVisibility = MemoVisibility.PRIVATE)
        viewModel.uiState.first { it.initialized }

        val state = viewModel.uiState.value
        assertTrue(state.isEditMode)
        assertEquals("m1", state.memoIdentifier)
        assertEquals("hello world", state.text.text)
        assertEquals("hello world".length, state.text.selection.start)
        assertEquals(MemoVisibility.PROTECTED, state.visibility)
        assertEquals(1, state.attachments.size)
        assertFalse(state.hasUnsavedChanges)
    }

    @Test
    fun `start is idempotent once initialized`() = runTest(testDispatcher) {
        repository.memosById["m1"] = editTarget()
        val viewModel = newViewModel()
        viewModel.start("m1", shareContent = null, defaultVisibility = MemoVisibility.PRIVATE)
        viewModel.uiState.first { it.initialized }

        viewModel.start(null, shareContent = null, defaultVisibility = MemoVisibility.PRIVATE)

        assertEquals("hello world", viewModel.uiState.value.text.text)
        assertTrue(viewModel.uiState.value.isEditMode)
    }

    @Test
    fun `create branch restores text saved across process death over any draft`() = runTest(testDispatcher) {
        val savedStateHandle = SavedStateHandle(mapOf("editor_text" to "saved notes"))
        val viewModel = newViewModel(savedStateHandle)

        viewModel.start(null, shareContent = null, defaultVisibility = MemoVisibility.PUBLIC)
        viewModel.uiState.first { it.initialized }

        assertEquals("saved notes", viewModel.uiState.value.text.text)
        assertEquals(MemoVisibility.PUBLIC, viewModel.uiState.value.visibility)
        assertFalse(viewModel.uiState.value.isEditMode)
    }

    @Test
    fun `share branch seeds text and keeps share semantics on hide`() = runTest(testDispatcher) {
        val viewModel = newViewModel()

        viewModel.start(null, shareContent = ShareContent(text = "shared text"), defaultVisibility = MemoVisibility.PRIVATE)
        viewModel.uiState.first { it.initialized }

        assertEquals("shared text", viewModel.uiState.value.text.text)
        // Share payloads must not be wiped by the bottom-sheet hide path.
        viewModel.onEditorHidden()
        assertEquals("shared text", viewModel.uiState.value.text.text)
    }

    @Test
    fun `setText persists into saved state for process death recovery`() = runTest(testDispatcher) {
        val savedStateHandle = SavedStateHandle()
        val viewModel = newViewModel(savedStateHandle)

        viewModel.setText(TextFieldValue("typing away"))

        assertEquals("typing away", savedStateHandle.get<String>("editor_text"))
        assertEquals("typing away", viewModel.uiState.value.text.text)
        assertTrue(viewModel.uiState.value.canSubmit)
    }

    @Test
    fun `submit create emits Submitted, records the memo and clears the text`() = runTest(testDispatcher) {
        val savedStateHandle = SavedStateHandle(mapOf("editor_text" to "new memo"))
        val viewModel = newViewModel(savedStateHandle)
        val events = collectEvents(viewModel)
        viewModel.start(null, shareContent = null, defaultVisibility = MemoVisibility.PUBLIC)
        viewModel.uiState.first { it.initialized }

        viewModel.submit()
        viewModel.uiState.first { !it.submitting }
        events.first { list -> list.contains(EditorEvent.Submitted) }

        assertEquals(1, repository.createCalls.size)
        assertEquals(FakeMemoRepository.CreateCall("new memo", MemoVisibility.PUBLIC), repository.createCalls.single())
        assertEquals("", viewModel.uiState.value.text.text)
        assertFalse(viewModel.uiState.value.submitting)
        assertFalse(viewModel.uiState.value.isEditMode)
    }

    @Test
    fun `submit edit updates the target and keeps the text`() = runTest(testDispatcher) {
        repository.memosById["m1"] = editTarget()
        val viewModel = newViewModel()
        val events = collectEvents(viewModel)
        viewModel.start("m1", shareContent = null, defaultVisibility = MemoVisibility.PRIVATE)
        viewModel.uiState.first { it.initialized }
        viewModel.setText(TextFieldValue("hello world edited"))

        viewModel.submit()
        viewModel.uiState.first { !it.submitting }
        events.first { list -> list.contains(EditorEvent.Submitted) }

        assertEquals(1, repository.updateCalls.size)
        assertEquals("m1", repository.updateCalls.single().identifier)
        assertEquals("hello world edited", repository.updateCalls.single().content)
        // Edit mode never clears the working text.
        assertEquals("hello world edited", viewModel.uiState.value.text.text)
    }

    @Test
    fun `submit failure reports a message and keeps everything intact`() = runTest(testDispatcher) {
        repository.failWrites = true
        val savedStateHandle = SavedStateHandle(mapOf("editor_text" to "draft text"))
        val viewModel = newViewModel(savedStateHandle)
        val events = collectEvents(viewModel)
        viewModel.start(null, shareContent = null, defaultVisibility = MemoVisibility.PRIVATE)
        viewModel.uiState.first { it.initialized }

        viewModel.submit()
        viewModel.uiState.first { !it.submitting }
        val delivered = events.first { list -> list.any { it is EditorEvent.ShowMessage } }

        assertFalse(delivered.contains(EditorEvent.Submitted))
        assertEquals("draft text", viewModel.uiState.value.text.text)
        assertFalse(viewModel.uiState.value.submitting)
    }

    @Test
    fun `hiding the editor discards a pure tag selection`() = runTest(testDispatcher) {
        val savedStateHandle = SavedStateHandle(mapOf("editor_text" to "#work "))
        val viewModel = newViewModel(savedStateHandle)
        viewModel.start(null, shareContent = null, defaultVisibility = MemoVisibility.PRIVATE)
        viewModel.uiState.first { it.initialized }

        viewModel.onEditorHidden()

        assertEquals("", viewModel.uiState.value.text.text)
    }

    @Test
    fun `hiding the editor keeps a real draft`() = runTest(testDispatcher) {
        val savedStateHandle = SavedStateHandle(mapOf("editor_text" to "real draft text"))
        val viewModel = newViewModel(savedStateHandle)
        viewModel.start(null, shareContent = null, defaultVisibility = MemoVisibility.PRIVATE)
        viewModel.uiState.first { it.initialized }

        viewModel.onEditorHidden()

        assertEquals("real draft text", viewModel.uiState.value.text.text)
    }

    @Test
    fun `editor ui state derived flags behave`() {
        val base = EditorUiState(initialized = true)

        assertFalse(base.copy(text = TextFieldValue("same"), initialContent = "same").hasUnsavedChanges)
        assertTrue(base.copy(text = TextFieldValue("same"), initialContent = "same", attachments = listOf(
            Attachment(id = "a", filename = "f", uri = "u"),
        )).hasUnsavedChanges)
        assertTrue(base.copy(text = TextFieldValue("x")).canSubmit)
        assertTrue(base.copy(attachments = listOf(Attachment(id = "a", filename = "f", uri = "u"))).canSubmit)
        assertFalse(base.canSubmit)
    }
}
