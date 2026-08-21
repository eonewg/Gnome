package me.mudkip.moememos

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
import me.mudkip.moememos.ui.page.common.Navigation
import me.mudkip.moememos.ui.page.memos.QuickMemoLaunchPage
import me.mudkip.moememos.ui.security.AppLockGate
import me.mudkip.moememos.ui.util.preferHighestRefreshRate
import me.mudkip.moememos.viewmodel.LocalMemos
import me.mudkip.moememos.viewmodel.LocalUserState
import me.mudkip.moememos.viewmodel.MemosViewModel
import me.mudkip.moememos.viewmodel.UserStateViewModel

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    private val userStateViewModel: UserStateViewModel by viewModels()
    private val memosViewModel: MemosViewModel by viewModels()

    companion object {
        const val ACTION_NEW_MEMO = "me.mudkip.moememos.action.NEW_MEMO"
        const val ACTION_QUICK_MEMO = "me.mudkip.moememos.action.QUICK_MEMO"
        const val ACTION_EDIT_MEMO = "me.mudkip.moememos.action.EDIT_MEMO"
        const val ACTION_VIEW_MEMO = "me.mudkip.moememos.action.VIEW_MEMO"
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
