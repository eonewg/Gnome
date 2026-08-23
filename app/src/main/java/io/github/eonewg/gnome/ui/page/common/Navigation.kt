package io.github.eonewg.gnome.ui.page.common

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.core.util.Consumer
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.window.core.layout.WindowSizeClass
import io.github.eonewg.gnome.MainActivity
import io.github.eonewg.gnome.data.model.Account
import io.github.eonewg.gnome.data.model.ShareContent
import io.github.eonewg.gnome.feature.account.AccountSessionViewModel
import io.github.eonewg.gnome.feature.drawer.DrawerViewModel
import io.github.eonewg.gnome.feature.editor.EditorRoute
import io.github.eonewg.gnome.feature.memo.MemoDetailRoute
import io.github.eonewg.gnome.feature.search.SearchRoute
import io.github.eonewg.gnome.feature.stats.StatsDetailRoute
import io.github.eonewg.gnome.feature.stats.StatsRoute
import io.github.eonewg.gnome.feature.tag.TagMemoRoute
import io.github.eonewg.gnome.feature.timeline.DateMemoRoute
import io.github.eonewg.gnome.feature.timeline.TimelineRoute
import io.github.eonewg.gnome.nav.AccountKey
import io.github.eonewg.gnome.nav.AddAccountKey
import io.github.eonewg.gnome.nav.ArchivedKey
import io.github.eonewg.gnome.nav.DateKey
import io.github.eonewg.gnome.nav.EditorKey
import io.github.eonewg.gnome.nav.ExploreKey
import io.github.eonewg.gnome.nav.GnomeNavKey
import io.github.eonewg.gnome.nav.GnomeNavigator
import io.github.eonewg.gnome.nav.LoginKey
import io.github.eonewg.gnome.nav.MemoDetailKey
import io.github.eonewg.gnome.nav.ResourcesKey
import io.github.eonewg.gnome.nav.SearchKey
import io.github.eonewg.gnome.nav.SettingsKey
import io.github.eonewg.gnome.nav.ShareKey
import io.github.eonewg.gnome.nav.StatsDetailKey
import io.github.eonewg.gnome.nav.StatsKey
import io.github.eonewg.gnome.nav.TagKey
import io.github.eonewg.gnome.nav.TimelineKey
import io.github.eonewg.gnome.nav.isDrawerScoped
import io.github.eonewg.gnome.nav.isDrawerSwitchDestination
import io.github.eonewg.gnome.ui.component.SideDrawer
import io.github.eonewg.gnome.ui.page.account.AccountPage
import io.github.eonewg.gnome.ui.page.account.AddAccountPage
import io.github.eonewg.gnome.ui.page.login.LoginPage
import io.github.eonewg.gnome.ui.page.memos.ArchivedMemoPage
import io.github.eonewg.gnome.ui.page.memos.ExplorePage
import io.github.eonewg.gnome.ui.page.resource.ResourceListPage
import io.github.eonewg.gnome.ui.page.settings.SettingsPage
import io.github.eonewg.gnome.ui.theme.GnomeDesign
import io.github.eonewg.gnome.ui.theme.GnomeTheme
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Persists the incoming share payload across activity recreation (rotation,
 * process death): the SaveableStateHolder restores the back stack, and this
 * restores the [ShareContent] the ShareKey entry renders. The empty payload
 * round-trips as null so the editor treats it like no share at all.
 */
private val ShareContentSaver = listSaver<ShareContent?, Any>(
    save = { content ->
        if (content == null) {
            listOf("")
        } else {
            listOf(content.text) + content.images.map { it.toString() }
        }
    },
    restore = { saved ->
        val text = saved.firstOrNull() as? String ?: ""
        val images = saved.drop(1).mapNotNull { (it as? String)?.toUri() }
        if (saved.size > 1 || text.isNotEmpty()) ShareContent(text, images) else null
    },
)

/**
 * The single Navigation 3 host: one typed back stack, one [NavDisplay], and a
 * drawer that wraps its root destinations plus memo child pages. The legacy
 * root/inner double NavHost is gone; drawer roots replace one another while
 * true child pages continue to push onto the typed stack.
 */
@Composable
fun Navigation() {
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val backStack = rememberNavBackStack(TimelineKey)
    val navigator = remember { GnomeNavigator(backStack) }
    val accountSessionViewModel: AccountSessionViewModel = hiltViewModel()
    val drawerViewModel: DrawerViewModel = hiltViewModel()
    val drawerUiState by drawerViewModel.uiState.collectAsStateWithLifecycle()
    val currentAccount by accountSessionViewModel.currentAccount.collectAsStateWithLifecycle()
    val hasExplore = currentAccount !is Account.Local
    val context = LocalContext.current
    // Drawer roots replace NavEntries; the host owner keeps the Timeline snapshot warm
    // without retaining those roots in the navigation back stack.
    val timelineViewModelStoreOwner = checkNotNull(LocalViewModelStoreOwner.current)
    var shareContent by rememberSaveable(stateSaver = ShareContentSaver) {
        mutableStateOf<ShareContent?>(null)
    }
    var quickMemoRequestId by remember { mutableStateOf(0L) }
    var memoInputActive by rememberSaveable { mutableStateOf(false) }
    var drawerNavigationTarget by remember { mutableStateOf<GnomeNavKey?>(null) }
    val colors = GnomeDesign.colors
    val currentKey = backStack.lastOrNull() as? GnomeNavKey

    fun drawerNavigate(
        target: GnomeNavKey? = null,
        action: () -> Unit,
    ) {
        drawerNavigationTarget = target
        action()
        scope.launch {
            try {
                drawerState.close()
            } finally {
                if (drawerNavigationTarget == target) {
                    drawerNavigationTarget = null
                }
            }
        }
    }

    val drawerContent: @Composable () -> Unit = {
        SideDrawer(
            uiState = drawerUiState,
            currentKey = currentKey,
            onStatsClick = {
                drawerNavigate { navigator.navigate(StatsKey, singleTop = true) }
            },
            onMemosClick = {
                drawerNavigate(TimelineKey) { navigator.switchDrawerDestination(TimelineKey) }
            },
            onExploreClick = {
                // Local accounts have no explore feed; keep the drawer item visible
                // but fall back to the timeline instead of flashing an empty page.
                val target = if (hasExplore) ExploreKey else TimelineKey
                drawerNavigate(target) {
                    navigator.switchDrawerDestination(target)
                }
            },
            onResourcesClick = {
                drawerNavigate(ResourcesKey) { navigator.switchDrawerDestination(ResourcesKey) }
            },
            onArchivedClick = {
                drawerNavigate(ArchivedKey) { navigator.switchDrawerDestination(ArchivedKey) }
            },
            onSettingsClick = {
                drawerNavigate(SettingsKey) { navigator.switchDrawerDestination(SettingsKey) }
            },
            onTagClick = { tag ->
                val target = TagKey(tag)
                drawerNavigate(target) { navigator.switchDrawerDestination(target) }
            },
            onDateClick = { date ->
                drawerNavigate { navigator.navigate(DateKey(date.toString()), singleTop = true) }
            },
        )
    }

    val entryProvider = entryProvider<NavKey> {
        entry<TimelineKey> {
            TimelineRoute(
                viewModelStoreOwner = timelineViewModelStoreOwner,
                drawerState = drawerState,
                navigator = navigator,
                quickMemoRequestId = quickMemoRequestId,
                onMemoInputActiveChange = { memoInputActive = it },
            )
        }

        entry<ArchivedKey> {
            ArchivedMemoPage(drawerState = drawerState)
        }

        entry<ExploreKey> {
            ExplorePage(drawerState = drawerState)
        }

        entry<SearchKey> {
            SearchRoute(navigator = navigator)
        }

        // Reuse one Tag composition so the Scaffold stays stable while only its content animates.
        entry<TagKey>(clazzContentKey = { TagDestinationContentKey }) { key ->
            TagMemoRoute(
                drawerState = drawerState,
                tag = key.tag,
                navigator = navigator,
            )
        }

        entry<DateKey> { key ->
            val date = runCatching { LocalDate.parse(key.date) }.getOrNull() ?: LocalDate.now()
            DateMemoRoute(
                drawerState = drawerState,
                date = date,
                navigator = navigator,
            )
        }

        entry<MemoDetailKey> { key ->
            MemoDetailRoute(memoIdentifier = key.memoId, navigator = navigator)
        }

        entry<EditorKey> { key ->
            EditorRoute(memoIdentifier = key.memoId, onFinished = { navigator.goBack() })
        }

        entry<ShareKey> {
            EditorRoute(shareContent = shareContent, onFinished = { navigator.goBack() })
        }

        entry<StatsKey> {
            StatsRoute(navigator = navigator)
        }

        entry<StatsDetailKey> {
            StatsDetailRoute(navigator = navigator)
        }

        entry<ResourcesKey> {
            ResourceListPage(
                drawerState = drawerState,
                onBack = { navigator.goBack() },
            )
        }

        entry<SettingsKey> {
            SettingsPage(drawerState = drawerState, navigator = navigator)
        }

        entry<AddAccountKey> {
            AddAccountPage(navigator = navigator)
        }

        entry<LoginKey> {
            LoginPage(navigator = navigator)
        }

        entry<AccountKey> { key ->
            AccountPage(navigator = navigator, selectedAccountKey = key.accountKey)
        }
    }

    val content: @Composable () -> Unit = {
        // Preserve the low-cost spring fade while adding only a subtle direction cue.
        // Larger translation or scale makes destination changes feel less direct.
        val transitionOffsetPx = with(LocalDensity.current) { 8.dp.roundToPx() }
        val entries = rememberDecoratedNavEntries(
            backStack = backStack,
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
                rememberViewModelStoreNavEntryDecorator<NavKey>(),
            ),
            entryProvider = entryProvider,
        )
        NavDisplay(
            entries = entries,
            onBack = { navigator.goBack() },
            modifier = Modifier.background(MaterialTheme.colorScheme.surface),
            transitionSpec = {
                // The back stack has already changed here, so currentKey is the incoming key.
                // A Drawer switch lets the sheet/scrim carry the motion; only the incoming
                // content gets a near-opaque reveal and the outgoing page never fades away.
                if (drawerNavigationTarget == currentKey) {
                    fadeIn(
                        initialAlpha = DrawerDestinationInitialAlpha,
                        animationSpec = tween(
                            durationMillis = DrawerDestinationRevealDurationMillis,
                            easing = FastOutSlowInEasing,
                        ),
                    ) togetherWith ExitTransition.None
                } else if (currentKey is TagKey) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else if (isDrawerSwitchDestination(currentKey)) {
                    fadeIn(
                        animationSpec = tween(
                            durationMillis = TopLevelEnterDurationMillis,
                            easing = FastOutSlowInEasing,
                        )
                    ) togetherWith fadeOut(
                        animationSpec = tween(
                            durationMillis = TopLevelExitDurationMillis,
                            easing = FastOutSlowInEasing,
                        )
                    )
                } else {
                    (fadeIn() + slideInHorizontally(
                        animationSpec = tween(
                            durationMillis = 200,
                            easing = FastOutSlowInEasing,
                        ),
                        initialOffsetX = { transitionOffsetPx },
                    )) togetherWith fadeOut()
                }
            },
            popTransitionSpec = {
                fadeIn() togetherWith (fadeOut() + slideOutHorizontally(
                    animationSpec = tween(
                        durationMillis = 200,
                        easing = FastOutSlowInEasing,
                    ),
                    targetOffsetX = { transitionOffsetPx },
                ))
            },
            predictivePopTransitionSpec = { _ ->
                fadeIn() togetherWith (fadeOut() + slideOutHorizontally(
                    animationSpec = tween(
                        durationMillis = 200,
                        easing = FastOutSlowInEasing,
                    ),
                    targetOffsetX = { transitionOffsetPx },
                ))
            },
        )
    }

    GnomeTheme {
        if (windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)) {
            if (isDrawerScoped(currentKey)) {
                PermanentNavigationDrawer(
                    drawerContent = {
                        PermanentDrawerSheet(
                            drawerContainerColor = colors.cardBackground,
                        ) {
                            drawerContent()
                        }
                    }
                ) {
                    content()
                }
            } else {
                content()
            }
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = isDrawerScoped(currentKey) && !memoInputActive,
                drawerContent = {
                    ModalDrawerSheet(
                        modifier = Modifier.fillMaxWidth(0.84f),
                        drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
                        drawerContainerColor = colors.cardBackground,
                        drawerTonalElevation = 0.dp,
                    ) {
                        drawerContent()
                    }
                }
            ) {
                content()
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!accountSessionViewModel.hasAnyAccount()) {
            navigator.resetTo(AddAccountKey)
        }
    }

    LaunchedEffect(memoInputActive) {
        if (memoInputActive && drawerState.isOpen) {
            drawerState.close()
        }
    }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch {
            drawerState.close()
        }
    }

    fun handleIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> {
                shareContent = ShareContent.parseIntent(intent)
                navigator.navigate(ShareKey)
            }
            Intent.ACTION_VIEW -> {
                when (intent.getStringExtra("action")) {
                    "compose" -> navigator.navigate(EditorKey())
                    "search" -> navigator.navigate(SearchKey)
                }
            }
            MainActivity.ACTION_NEW_MEMO -> {
                navigator.navigate(EditorKey())
            }
            MainActivity.ACTION_QUICK_MEMO -> {
                quickMemoRequestId += 1
                // Back to the timeline (clearing whatever is above it) before the
                // quick request opens the inline editor.
                navigator.popUpTo(TimelineKey)
                // Prevent an Activity recreation from reopening an already consumed request.
                intent.action = null
            }
            MainActivity.ACTION_EDIT_MEMO -> {
                val memoId = intent.getStringExtra(MainActivity.EXTRA_MEMO_ID)
                if (memoId != null) {
                    navigator.navigate(EditorKey(memoId))
                }
            }
            MainActivity.ACTION_VIEW_MEMO -> {
                val memoId = intent.getStringExtra(MainActivity.EXTRA_MEMO_ID)
                if (memoId != null) {
                    navigator.navigate(MemoDetailKey(memoId))
                }
            }
        }
    }

    LaunchedEffect(context) {
        if (context is ComponentActivity && context.intent != null) {
            handleIntent(context.intent)
        }
    }

    DisposableEffect(context) {
        val activity = context as? ComponentActivity

        val listener = Consumer<Intent> {
            handleIntent(it)
        }

        activity?.addOnNewIntentListener(listener)

        onDispose {
            activity?.removeOnNewIntentListener(listener)
        }
    }
}

private const val TagDestinationContentKey = "TagDestination"
private const val DrawerDestinationInitialAlpha = 0.95f
private const val DrawerDestinationRevealDurationMillis = 180
private const val TopLevelExitDurationMillis = 120
private const val TopLevelEnterDurationMillis = 180
