package io.github.eonewg.gnome.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

@LargeTest
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun aCriticalUserJourneys() = baselineProfileRule.collect(
        packageName = TargetPackage,
        includeInStartupProfile = false,
        maxIterations = 5,
        stableIterations = 3,
        filterPredicate = { !it.contains("BenchmarkFixtureActivity") },
    ) {
        pressHome()
        startActivityAndWait()
        GnomeJourneys.prepareFixtureData()
        GnomeJourneys.runCriticalJourneys()
    }

    @Test
    fun bColdLaunch() = baselineProfileRule.collect(
        packageName = TargetPackage,
        includeInStartupProfile = true,
        maxIterations = 5,
        stableIterations = 3,
        filterPredicate = { !it.contains("BenchmarkFixtureActivity") },
    ) {
        pressHome()
        startActivityAndWait()
        GnomeJourneys.completeFirstRunIfNeeded()
    }
}
