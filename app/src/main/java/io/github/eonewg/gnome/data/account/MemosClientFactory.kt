package io.github.eonewg.gnome.data.account

import com.skydoves.sandwich.retrofit.adapters.ApiResponseCallAdapterFactory
import io.github.eonewg.gnome.data.api.MemosV0Api
import io.github.eonewg.gnome.data.api.MemosV1Api
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds OkHttp/Retrofit clients for Memos servers. This is the only place
 * that knows how a raw host + access token become an authenticated API
 * surface; it knows nothing about account persistence or Room. The bearer
 * token is attached by a network interceptor scoped to the server's own
 * scheme/host/port so unrelated requests (e.g. OAuth) stay untouched.
 */
@Singleton
class MemosClientFactory @Inject constructor(
    private val baseClient: OkHttpClient,
) {
    private val networkJson = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    fun createV0Client(host: String, accessToken: String?): Pair<OkHttpClient, MemosV0Api> {
        val client = withTokenInterceptor(host, accessToken)
        return client to Retrofit.Builder()
            .baseUrl(host)
            .client(client)
            .addConverterFactory(networkJson.asConverterFactory("application/json".toMediaType()))
            .addCallAdapterFactory(ApiResponseCallAdapterFactory.create())
            .build()
            .create(MemosV0Api::class.java)
    }

    fun createV1Client(host: String, accessToken: String?): Pair<OkHttpClient, MemosV1Api> {
        val client = withTokenInterceptor(host, accessToken)
        return client to Retrofit.Builder()
            .baseUrl(host)
            .client(client)
            .addConverterFactory(networkJson.asConverterFactory("application/json".toMediaType()))
            .addCallAdapterFactory(ApiResponseCallAdapterFactory.create())
            .build()
            .create(MemosV1Api::class.java)
    }

    val baseHttpClient: OkHttpClient get() = baseClient

    private fun withTokenInterceptor(host: String, accessToken: String?): OkHttpClient {
        if (accessToken.isNullOrEmpty()) {
            return baseClient
        }
        return baseClient.newBuilder().addNetworkInterceptor { chain ->
            var request = chain.request()
            if (shouldAttachAccessToken(request.url, host)) {
                request = request.newBuilder().addHeader("Authorization", "Bearer $accessToken")
                    .build()
            }
            chain.proceed(request)
        }.build()
    }

    private fun shouldAttachAccessToken(requestUrl: HttpUrl, host: String): Boolean {
        val baseUrl = host.toHttpUrlOrNull() ?: return false
        return requestUrl.scheme == baseUrl.scheme &&
            requestUrl.host == baseUrl.host &&
            requestUrl.port == baseUrl.port
    }
}
