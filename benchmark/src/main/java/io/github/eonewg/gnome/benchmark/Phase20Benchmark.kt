package io.github.eonewg.gnome.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class Phase20Benchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun none() = measure(CompilationMode.None())

    @Test
    fun baselineProfile() = measure(
        CompilationMode.Partial(
            baselineProfileMode = BaselineProfileMode.Require,
            warmupIterations = 0,
        )
    )

    @Test
    fun speedProfile() = measure(
        CompilationMode.Partial(
            baselineProfileMode = BaselineProfileMode.Disable,
            warmupIterations = 5,
        )
    )

    private fun measure(compilationMode: CompilationMode) {
        benchmarkRule.measureRepeated(
            packageName = TargetPackage,
            metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
            compilationMode = compilationMode,
            startupMode = StartupMode.COLD,
            iterations = 5,
            setupBlock = { pressHome() },
        ) {
            startActivityAndWait()
            GnomeJourneys.waitForTimeline()
            GnomeJourneys.runCriticalJourneys()
        }
    }

    companion object {
        @JvmStatic
        @BeforeClass
        fun prepareFixture() {
            GnomeJourneys.launchFromTestProcess()
            GnomeJourneys.prepareFixtureData()
            GnomeJourneys.device.pressHome()
        }
    }
}
