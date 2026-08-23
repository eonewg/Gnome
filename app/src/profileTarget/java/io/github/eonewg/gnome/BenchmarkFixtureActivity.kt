package io.github.eonewg.gnome

import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.github.eonewg.gnome.core.model.MemoVisibility
import io.github.eonewg.gnome.data.model.Account
import io.github.eonewg.gnome.data.model.LocalAccount
import io.github.eonewg.gnome.data.service.AccountService
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Seeds deterministic local data for the isolated profileTarget package only. */
@AndroidEntryPoint
class BenchmarkFixtureActivity : ComponentActivity() {
    @Inject
    lateinit var accountService: AccountService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val status = TextView(this).apply { text = "GNOME_BENCHMARK_SEEDING" }
        setContentView(status)

        lifecycleScope.launch {
            if (accountService.currentAccount.first() == null) {
                accountService.addAccount(
                    Account.Local(LocalAccount(startDateEpochSecond = Instant.now().epochSecond))
                )
            }
            val repository = accountService.getMemoRepository()
            if (repository.observeTimeline().first().none { it.content.startsWith(FixturePrefix) }) {
                repeat(FixtureCount) { index ->
                    repository.createMemo(
                        content = "$FixturePrefix ${index + 1} performance",
                        visibility = MemoVisibility.PRIVATE,
                    )
                }
            }
            status.text = FixtureReady
        }
    }

    companion object {
        const val FixtureReady = "GNOME_BENCHMARK_READY"
        private const val FixturePrefix = "gnome benchmark sample"
        private const val FixtureCount = 10
    }
}