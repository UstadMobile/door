package com.ustadmobile.door

import com.ustadmobile.door.log.DoorLogger
import com.ustadmobile.door.log.NapierDoorLogger
import io.ktor.client.*
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient


actual class RepositoryConfig internal constructor(
    actual val context: Any,
    actual val endpoint: String,
    actual val auth: String,
    actual val nodeId: Long,
    actual val httpClient: HttpClient,
    val okHttpClient: OkHttpClient,
    actual val json: Json,
    actual val logger: DoorLogger,
    actual val dbName: String,
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
            val logger: DoorLogger,
            val dbName: String,
        ) {

            fun build() : RepositoryConfig{
                return RepositoryConfig(
                    context, endpoint, auth, nodeId, httpClient, okHttpClient, json, logger, dbName
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
            logger: DoorLogger = NapierDoorLogger(),
            dbName: String = endpoint,
            json: Json = Json { encodeDefaults = true },
            block: Builder.() -> Unit = {}
        ) : RepositoryConfig {
            val builder = Builder(context, endpoint, nodeId, auth, httpClient, okHttpClient, json, logger, dbName)
            block(builder)
            return builder.build()
        }

    }

}