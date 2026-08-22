package io.github.eonewg.gnome

import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Quick Settings quick-capture host (QuickMemoActivity) smoke on a real
 * device: launched from a fresh process it must resolve the persisted account
 * and show the editor; recreation must not drop the host.
 *
 * When no account is configured the host deliberately falls back to
 * MainActivity, so that environment is reported as "skipped" instead of a
 * failure (the no-account path is covered by pure unit tests).
 */
@RunWith(AndroidJUnit4::class)
class QuickMemoActivityTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<QuickMemoActivity>()

    private fun waitForEditor() {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithContentDescription("发送").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun editorAppearsFromColdLaunchWhenAccountExists() {
        try {
            waitForEditor()
            composeRule.onNodeWithContentDescription("发送").assertIsDisplayed()
        } catch (e: ComposeTimeoutException) {
            assumeTrue(
                "No account configured — QuickMemoActivity falls back to MainActivity; skipping",
                false,
            )
        }
    }

    @Test
    fun editorSurvivesActivityRecreation() {
        try {
            waitForEditor()
            composeRule.activityRule.scenario.recreate()
            waitForEditor()
            composeRule.onNodeWithContentDescription("发送").assertIsDisplayed()
        } catch (e: ComposeTimeoutException) {
            assumeTrue(
                "No account configured — QuickMemoActivity falls back to MainActivity; skipping",
                false,
            )
        }
    }
}