package io.github.eonewg.gnome

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import io.github.eonewg.gnome.ui.page.common.LocalRootNavController
import io.github.eonewg.gnome.ui.page.memoinput.MemoInputPage
import io.github.eonewg.gnome.ui.security.AppLockGate
import io.github.eonewg.gnome.ui.theme.GnomeTheme
import io.github.eonewg.gnome.viewmodel.LocalMemos
import io.github.eonewg.gnome.viewmodel.LocalUserState
import io.github.eonewg.gnome.viewmodel.MemosViewModel
import io.github.eonewg.gnome.viewmodel.UserStateViewModel

/** A short-lived host for the existing memo editor when opened from Quick Settings. */
@AndroidEntryPoint
class QuickMemoActivity : FragmentActivity() {
    private val userStateViewModel: UserStateViewModel by viewModels()
    private val memosViewModel: MemosViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            CompositionLocalProvider(
                LocalUserState provides userStateViewModel,
                LocalMemos provides memosViewModel,
            ) {
                AppLockGate {
                    GnomeTheme {
                        val navController = rememberNavController()
                        var editorReady by remember { mutableStateOf(false) }

                        CompositionLocalProvider(LocalRootNavController provides navController) {
                            if (editorReady) {
                                MemoInputPage(onFinished = ::finish)
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }

                        LaunchedEffect(Unit) {
                            if (!userStateViewModel.hasAnyAccount()) {
                                startActivity(
                                    Intent(this@QuickMemoActivity, MainActivity::class.java).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                    }
                                )
                                finish()
                                return@LaunchedEffect
                            }
                            userStateViewModel.loadCurrentUser()
                            editorReady = true
                        }
                    }
                }
            }
        }
    }
}
