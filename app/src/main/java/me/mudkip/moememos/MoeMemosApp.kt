package me.mudkip.moememos

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import dagger.hilt.android.HiltAndroidApp
import me.mudkip.moememos.data.service.AccountService
import me.mudkip.moememos.ui.security.AppLockSession
import okhttp3.Call
import javax.inject.Inject

@HiltAndroidApp
class MoeMemosApp : Application(), SingletonImageLoader.Factory {
    @Inject
    lateinit var accountService: AccountService

    companion object {
        @SuppressLint("StaticFieldLeak")
        lateinit var CONTEXT: Context
    }

    override fun attachBaseContext(base: Context?) {
        CONTEXT = this
        super.attachBaseContext(base)
    }

    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                AppLockSession.markAppForegrounded()
            }

            override fun onStop(owner: LifecycleOwner) {
                AppLockSession.markAppBackgrounded()
            }
        })
    }

    override fun newImageLoader(context: Context): ImageLoader =
        ImageLoader.Builder(context.applicationContext)
            .components {
                add(
                    OkHttpNetworkFetcherFactory(
                        callFactory = {
                            Call.Factory { request -> accountService.httpClient.newCall(request) }
                        }
                    )
                )
            }
            .build()
}
