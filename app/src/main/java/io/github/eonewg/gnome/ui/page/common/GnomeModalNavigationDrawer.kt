/*
 * Copyright 2021 The Android Open Source Project
 * Copyright 2026 Gnome contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.eonewg.gnome.ui.page.common

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.DrawerDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import io.github.eonewg.gnome.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val DrawerMotionSpec = tween<Float>(
    durationMillis = DrawerMotionDurationMillis,
    easing = FastOutSlowInEasing,
)

/** Drawer state whose programmatic and gesture-settle paths share one measured motion curve. */
@Stable
class GnomeDrawerState internal constructor(initialValue: DrawerValue) {
    internal val draggableState = AnchoredDraggableState(initialValue)

    val currentValue: DrawerValue
        get() = draggableState.settledValue

    val targetValue: DrawerValue
        get() = draggableState.targetValue

    val currentOffset: Float
        get() = draggableState.offset

    val isOpen: Boolean
        get() = currentValue == DrawerValue.Open

    val isClosed: Boolean
        get() = currentValue == DrawerValue.Closed

    val isAnimationRunning: Boolean
        get() = draggableState.isAnimationRunning

    /**
     * Opens the drawer using the same non-overshooting tween used after a drag settles.
     *
     * @throws CancellationException when another gesture or state change takes ownership.
     */
    suspend fun open() {
        draggableState.animateTo(DrawerValue.Open, DrawerMotionSpec)
    }

    /**
     * Closes the drawer using the same non-overshooting tween used after a drag settles.
     *
     * @throws CancellationException when another gesture or state change takes ownership.
     */
    suspend fun close() {
        draggableState.animateTo(DrawerValue.Closed, DrawerMotionSpec)
    }

    companion object {
        fun Saver(): Saver<GnomeDrawerState, DrawerValue> = Saver(
            save = { it.currentValue },
            restore = { GnomeDrawerState(it) },
        )
    }
}

@Composable
fun rememberGnomeDrawerState(
    initialValue: DrawerValue = DrawerValue.Closed,
): GnomeDrawerState = rememberSaveable(saver = GnomeDrawerState.Saver()) {
    GnomeDrawerState(initialValue)
}

/**
 * A deliberately small Material modal-drawer shell. Material's sheet remains unchanged; only the
 * draggable state is owned here so open, close, Scrim tap, Back, and gesture settling can use the
 * same measured curve instead of two different internal Material springs.
 */
@Composable
fun GnomeModalNavigationDrawer(
    drawerContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    drawerState: GnomeDrawerState = rememberGnomeDrawerState(),
    gesturesEnabled: Boolean = true,
    scrimColor: Color = DrawerDefaults.scrimColor,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val navigationMenu = stringResource(R.string.menu)
    val closeDrawer = stringResource(R.string.close_drawer)
    var anchorsInitialized by remember { mutableStateOf(false) }
    var closedAnchor by remember { mutableFloatStateOf(0f) }
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val flingBehavior = AnchoredDraggableDefaults.flingBehavior(
        state = drawerState.draggableState,
        positionalThreshold = { distance -> distance * DrawerPositionalThreshold },
        animationSpec = DrawerMotionSpec,
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .anchoredDraggable(
                state = drawerState.draggableState,
                orientation = Orientation.Horizontal,
                enabled = gesturesEnabled,
                reverseDirection = isRtl,
                flingBehavior = flingBehavior,
            ),
    ) {
        Box { content() }
        DrawerScrim(
            open = drawerState.isOpen,
            onClose = {
                if (gesturesEnabled) {
                    scope.launch { drawerState.close() }
                }
            },
            fraction = {
                drawerState.currentOffset.takeIf { it.isFinite() }
                    ?.let { calculateFraction(closedAnchor, 0f, it) }
                    ?: 0f
            },
            color = scrimColor,
            closeDescription = closeDrawer,
        )
        Layout(
            content = drawerContent,
            modifier = Modifier
                .offset {
                    val offset = drawerState.currentOffset
                    IntOffset(
                        x = if (offset.isFinite()) {
                            offset.roundToInt()
                        } else {
                            -DrawerDefaults.MaximumDrawerWidth.roundToPx()
                        },
                        y = 0,
                    )
                }
                .semantics {
                    paneTitle = navigationMenu
                    if (drawerState.isOpen) {
                        dismiss {
                            scope.launch { drawerState.close() }
                            true
                        }
                    }
                },
        ) { measurables, constraints ->
            val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)
            val placeables = measurables.map { it.measure(looseConstraints) }
            val width = placeables.maxOfOrNull { it.width } ?: 0
            val height = placeables.maxOfOrNull { it.height } ?: 0

            layout(width, height) {
                val calculatedClosedAnchor = -width.toFloat()
                if (!anchorsInitialized || closedAnchor != calculatedClosedAnchor) {
                    anchorsInitialized = true
                    closedAnchor = calculatedClosedAnchor
                    drawerState.draggableState.updateAnchors(
                        DraggableAnchors {
                            DrawerValue.Closed at calculatedClosedAnchor
                            DrawerValue.Open at 0f
                        },
                    )
                }
                placeables.forEach { it.placeRelative(0, 0) }
            }
        }
    }
}

@Composable
private fun DrawerScrim(
    open: Boolean,
    onClose: () -> Unit,
    fraction: () -> Float,
    color: Color,
    closeDescription: String,
) {
    val dismissModifier = if (open) {
        Modifier
            .pointerInput(onClose) { detectTapGestures { onClose() } }
            .semantics(mergeDescendants = true) {
                contentDescription = closeDescription
                onClick {
                    onClose()
                    true
                }
            }
    } else {
        Modifier
    }

    Canvas(Modifier.fillMaxSize().then(dismissModifier)) {
        drawRect(color = color, alpha = fraction())
    }
}

private fun calculateFraction(start: Float, end: Float, position: Float): Float {
    if (start == end) return 0f
    return ((position - start) / (end - start)).coerceIn(0f, 1f)
}

internal const val DrawerMotionDurationMillis = 320
private const val DrawerPositionalThreshold = 0.5f
