package io.github.eonewg.gnome.ui.util

import android.app.Activity

/**
 * Prefers the fastest display mode that keeps the current physical resolution.
 * Android may still select a lower refresh rate for power, thermal, or system policy reasons.
 */
@Suppress("DEPRECATION")
fun Activity.preferHighestRefreshRate() {
    val display = windowManager.defaultDisplay
    val currentMode = display.mode
    val preferredMode = display.supportedModes
        .asSequence()
        .filter {
            it.physicalWidth == currentMode.physicalWidth &&
                it.physicalHeight == currentMode.physicalHeight
        }
        .maxByOrNull { it.refreshRate }
        ?: return

    val attributes = window.attributes
    if (attributes.preferredDisplayModeId != preferredMode.modeId) {
        attributes.preferredDisplayModeId = preferredMode.modeId
        window.attributes = attributes
    }
}
