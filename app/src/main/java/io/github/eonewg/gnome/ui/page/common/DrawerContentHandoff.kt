package io.github.eonewg.gnome.ui.page.common

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import io.github.eonewg.gnome.nav.GnomeNavKey
import io.github.eonewg.gnome.nav.TagKey
import io.github.eonewg.gnome.nav.isDrawerSwitchDestination

/** A visual handoff between two Drawer roots; it never changes the navigation back stack. */
internal data class DrawerContentHandoff(
    val outgoing: GnomeNavKey,
    val incoming: GnomeNavKey,
)

internal enum class DrawerContentHandoffRole {
    Outgoing,
    Incoming,
    None,
}

internal val LocalDrawerContentHandoffActive = staticCompositionLocalOf { false }

internal fun createDrawerContentHandoff(
    outgoing: GnomeNavKey?,
    incoming: GnomeNavKey?,
): DrawerContentHandoff? {
    if (outgoing == null || incoming == null || outgoing == incoming) return null
    if (!isDrawerSwitchDestination(outgoing) || !isDrawerSwitchDestination(incoming)) return null
    // Tag roots share one NavDisplay entry; their established content-only transition owns this.
    if (outgoing is TagKey && incoming is TagKey) return null
    return DrawerContentHandoff(outgoing = outgoing, incoming = incoming)
}

internal fun DrawerContentHandoff.roleOf(key: GnomeNavKey): DrawerContentHandoffRole = when (key) {
    outgoing -> DrawerContentHandoffRole.Outgoing
    incoming -> DrawerContentHandoffRole.Incoming
    else -> DrawerContentHandoffRole.None
}

internal fun DrawerContentHandoff.alphaFor(
    contentKey: GnomeNavKey,
    isTargetScene: Boolean,
): Float = when (roleOf(contentKey)) {
    DrawerContentHandoffRole.Outgoing -> if (isTargetScene) 0f else 1f
    DrawerContentHandoffRole.Incoming -> if (isTargetScene) 1f else 0f
    DrawerContentHandoffRole.None -> 1f
}

/**
 * Animates only a root destination's visual content. The destination Scaffold and app bar make
 * their container colors transparent while this is active, leaving the host background opaque.
 */
@Composable
internal fun DrawerDestinationContent(
    key: GnomeNavKey,
    handoff: DrawerContentHandoff?,
    onHandoffFinished: (DrawerContentHandoff) -> Unit,
    content: @Composable () -> Unit,
) {
    val animatedContentScope = LocalNavAnimatedContentScope.current
    val transition = animatedContentScope.transition
    val role = handoff?.roleOf(key) ?: DrawerContentHandoffRole.None
    val alpha by transition.animateFloat(
        transitionSpec = {
            when (role) {
                DrawerContentHandoffRole.Outgoing -> tween(
                    durationMillis = DrawerContentOutgoingDurationMillis,
                    easing = FastOutLinearInEasing,
                )
                DrawerContentHandoffRole.Incoming -> tween(
                    durationMillis = DrawerContentIncomingDurationMillis,
                    delayMillis = DrawerContentIncomingDelayMillis,
                    easing = FastOutSlowInEasing,
                )
                DrawerContentHandoffRole.None -> snap()
            }
        },
        label = "DrawerDestinationContentAlpha",
    ) { scene ->
        handoff?.alphaFor(key, isTargetScene = scene == transition.targetState) ?: 1f
    }

    LaunchedEffect(
        handoff,
        key,
        transition.currentState,
        transition.targetState,
        transition.isRunning,
    ) {
        val finishedHandoff = handoff ?: return@LaunchedEffect
        if (
            key == finishedHandoff.incoming &&
            transition.currentState == transition.targetState &&
            !transition.isRunning
        ) {
            onHandoffFinished(finishedHandoff)
        }
    }

    CompositionLocalProvider(LocalDrawerContentHandoffActive provides (role != DrawerContentHandoffRole.None)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { this.alpha = alpha },
        ) {
            content()
        }
    }
}

internal const val DrawerContentOutgoingDurationMillis = 190
internal const val DrawerContentIncomingDurationMillis = 230
internal const val DrawerContentIncomingDelayMillis = 40
