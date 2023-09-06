package com.ustadmobile.door

import io.ktor.client.*
import okhttp3.OkHttpClient
import kotlinx.serialization.json.Json


actual class RepositoryConfig internal constructor(
    actual val context: Any,
    actual val endpoint: String,
    actual val auth: String,
    actual val nodeId: Long,
    actual val httpClient: HttpClient,
    val okHttpClient: OkHttpClient,
    actual val json: Json,
) {

    companion object {

        class Builder internal constructor(
            val context: Any,
            val endpoint: String,
            val nodeId: Long,
            val auth: String,
            val httpClient: HttpClient,
            val okHttpClient: OkHttpClient,
            val json: Json,
        ) {

            fun build() : RepositoryConfig{
                return RepositoryConfig(
                    context, endpoint, auth, nodeId, httpClient, okHttpClient, json,
                )
            }

        }

        fun repositoryConfig(
            context: Any,
            endpoint: String,
            nodeId: Long,
            auth: String,
            httpClient: HttpClient,
            okHttpClient: OkHttpClient,
            json: Json = Json { encodeDefaults = true },
            block: Builder.() -> Unit = {}
        ) : RepositoryConfig {
            val builder = Builder(context, endpoint, nodeId, auth, httpClient, okHttpClient, json)
            block(builder)
            return builder.build()
        }

    }

}