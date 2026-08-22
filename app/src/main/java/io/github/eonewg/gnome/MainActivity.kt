package io.github.eonewg.gnome

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.AndroidEntryPoint
import io.github.eonewg.gnome.ui.page.common.Navigation
import io.github.eonewg.gnome.ui.page.memos.QuickMemoLaunchPage
import io.github.eonewg.gnome.ui.security.AppLockGate
import io.github.eonewg.gnome.ui.util.preferHighestRefreshRate
import io.github.eonewg.gnome.viewmodel.LocalMemos
import io.github.eonewg.gnome.viewmodel.LocalUserState
import io.github.eonewg.gnome.viewmodel.MemosViewModel
import io.github.eonewg.gnome.viewmodel.UserStateViewModel

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    private val userStateViewModel: UserStateViewModel by viewModels()
    private val memosViewModel: MemosViewModel by viewModels()

    companion object {
        const val ACTION_NEW_MEMO = "io.github.eonewg.gnome.action.NEW_MEMO"
        const val ACTION_QUICK_MEMO = "io.github.eonewg.gnome.action.QUICK_MEMO"
        const val ACTION_EDIT_MEMO = "io.github.eonewg.gnome.action.EDIT_MEMO"
        const val ACTION_VIEW_MEMO = "io.github.eonewg.gnome.action.VIEW_MEMO"
        const val EXTRA_MEMO_ID = "memoId"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialQuickMemo =
            savedInstanceState == null && intent.action == ACTION_QUICK_MEMO
        if (initialQuickMemo) {
            // Consume the cold-start request before the normal navigation tree is created.
            intent.action = null
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        }
        preferHighestRefreshRate()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        setContent {
            CompositionLocalProvider(
                LocalUserState provides userStateViewModel,
                LocalMemos provides memosViewModel
            ) {
                AppLockGate {
                    var quickMemoActive by rememberSaveable {
                        mutableStateOf(initialQuickMemo)
                    }
                    if (quickMemoActive) {
                        QuickMemoLaunchPage(
                            onFinished = { quickMemoActive = false },
                        )
                    } else {
                        Navigation()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}
