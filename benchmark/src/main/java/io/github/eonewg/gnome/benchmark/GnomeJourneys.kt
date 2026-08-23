package io.github.eonewg.gnome.benchmark

import android.content.Intent
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2

internal const val TargetPackage = "io.github.eonewg.gnome.benchmark"
internal const val FixtureMemoPrefix = "gnome benchmark sample"

internal object GnomeJourneys {
    private const val TimeoutMs = 10_000L
    private const val ShortTimeoutMs = 1_000L
    private const val FixtureReady = "GNOME_BENCHMARK_READY"

    val device: UiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    fun launchFromTestProcess() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = context.packageManager.getLaunchIntentForPackage(TargetPackage)
            ?: error("No launch intent for $TargetPackage")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        SystemClock.sleep(1_000)
    }

    fun prepareFixtureData() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent()
            .setClassName(TargetPackage, "io.github.eonewg.gnome.BenchmarkFixtureActivity")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        requireAny(By.text(FixtureReady))
        // The fixture updates the target process' persisted account state. A
        // still-running first-run activity can otherwise retain its stale
        // account snapshot and leave the benchmark on the account picker.
        device.executeShellCommand("am force-stop $TargetPackage")
        launchFromTestProcess()
        waitForTimeline()
        requireAny(By.textContains(FixtureMemoPrefix))
    }

    fun waitForTimeline() {
        requireAny(
            By.desc("Compose"),
            By.desc("撰写"),
            By.desc("Search"),
            By.desc("搜索"),
        )
    }

    fun completeFirstRunIfNeeded() {
        acceptFirstRunIfNeeded()
        waitForTimeline()
    }

    fun runCriticalJourneys() {
        scrollTimeline()
        openMemoAndReturn()
        openEditorAndReturn()
        openSearchAndReturn()
    }

    private fun acceptFirstRunIfNeeded() {
        findAny(
            ShortTimeoutMs,
            By.text("Add Local Account"),
            By.text("添加本地账户"),
        )?.clickCenter()
    }

    private fun scrollTimeline() {
        val width = device.displayWidth
        val height = device.displayHeight
        repeat(3) {
            swipeRaw(width / 2, height * 3 / 4, width / 2, height / 4)
            SystemClock.sleep(1_000)
        }
        repeat(3) {
            swipeRaw(width / 2, height / 4, width / 2, height * 3 / 4)
            SystemClock.sleep(1_000)
        }
    }

    private fun openMemoAndReturn() {
        val memo = requireAny(By.textContains(FixtureMemoPrefix))
        memo.clickCenter()
        requireAny(By.desc("Back"), By.desc("返回"))
        pressBackRaw()
        waitForTimeline()
    }

    private fun openEditorAndReturn() {
        requireAny(By.desc("Compose"), By.desc("撰写")).clickCenter()
        requireAny(By.clazz("android.widget.EditText"))
        pressBackRaw()
        if (device.hasObject(By.clazz("android.widget.EditText"))) pressBackRaw()
        waitForTimeline()
    }

    private fun openSearchAndReturn() {
        requireAny(By.desc("Search"), By.desc("搜索")).clickCenter()
        requireAny(By.clazz("android.widget.EditText"))
        requireAny(By.text("Cancel"), By.text("取消")).clickCenter()
        waitForTimeline()
    }

    private fun requireAny(vararg selectors: BySelector): UiObject2 =
        findAny(TimeoutMs, *selectors)
            ?: throw AssertionError("None of the requested UI elements appeared: ${selectors.joinToString()}")

    private fun findAny(timeoutMs: Long, vararg selectors: BySelector): UiObject2? {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        do {
            selectors.forEach { selector ->
                device.findObject(selector)?.let { return it }
            }
            SystemClock.sleep(100)
        } while (SystemClock.elapsedRealtime() < deadline)
        return null
    }

    private fun UiObject2.clickCenter() {
        val center = visibleCenter
        val downTime = SystemClock.uptimeMillis()
        injectMotion(MotionEvent.ACTION_DOWN, center.x.toFloat(), center.y.toFloat(), downTime)
        injectMotion(MotionEvent.ACTION_UP, center.x.toFloat(), center.y.toFloat(), downTime)
        SystemClock.sleep(300)
    }

    private fun swipeRaw(startX: Int, startY: Int, endX: Int, endY: Int) {
        val downTime = SystemClock.uptimeMillis()
        injectMotion(MotionEvent.ACTION_DOWN, startX.toFloat(), startY.toFloat(), downTime)
        repeat(12) { index ->
            val progress = (index + 1) / 12f
            injectMotion(
                MotionEvent.ACTION_MOVE,
                startX + (endX - startX) * progress,
                startY + (endY - startY) * progress,
                downTime,
            )
        }
        injectMotion(MotionEvent.ACTION_UP, endX.toFloat(), endY.toFloat(), downTime)
    }

    private fun injectMotion(action: Int, x: Float, y: Float, downTime: Long) {
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
        }
        try {
            check(InstrumentationRegistry.getInstrumentation().uiAutomation.injectInputEvent(event, false))
        } finally {
            event.recycle()
        }
    }

    private fun pressBackRaw() {
        val now = SystemClock.uptimeMillis()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        check(automation.injectInputEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0), false))
        check(automation.injectInputEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK, 0), false))
        SystemClock.sleep(300)
    }

}
