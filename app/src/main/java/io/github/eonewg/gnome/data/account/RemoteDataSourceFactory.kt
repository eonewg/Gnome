package io.github.eonewg.gnome.data.account

import io.github.eonewg.gnome.data.model.Account
import io.github.eonewg.gnome.data.remote.RemoteDataSource
import io.github.eonewg.gnome.data.remote.memos.MemosV0RemoteDataSource
import io.github.eonewg.gnome.data.remote.memos.MemosV1RemoteDataSource
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns a persisted remote account into the matching [RemoteDataSource]
 * together with its authenticated HTTP client. Local accounts have no
 * remote data source and yield null — callers fall back to local-only mode.
 */
@Singleton
class RemoteDataSourceFactory @Inject constructor(
    private val clientFactory: MemosClientFactory,
) {
    data class MemosRemote(
        val httpClient: OkHttpClient,
        val remoteDataSource: RemoteDataSource,
    )

    fun create(account: Account.MemosV0): MemosRemote {
        val (client, api) = clientFactory.createV0Client(account.info.host, account.info.accessToken)
        return MemosRemote(client, MemosV0RemoteDataSource(api, account))
    }

    fun create(account: Account.MemosV1): MemosRemote {
        val (client, api) = clientFactory.createV1Client(account.info.host, account.info.accessToken)
        return MemosRemote(client, MemosV1RemoteDataSource(api, account))
    }

    fun create(account: Account): MemosRemote? = when (account) {
        is Account.MemosV0 -> create(account)
        is Account.MemosV1 -> create(account)
        is Account.Local -> null
    }
}
