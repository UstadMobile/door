package com.ustadmobile.door

import com.ustadmobile.door.log.DoorLogger
import com.ustadmobile.door.log.NapierDoorLogger
import io.ktor.client.*
import kotlinx.serialization.json.Json

/**
 * Contains the configuration for a repository. It is created via a platform-specific builder that may have additional
 * dependencies on specific platforms.
 */
actual class RepositoryConfig internal constructor(
    actual val context: Any,
    actual val endpoint: String,
    actual val auth: String,
    actual val nodeId: Long,
    actual val httpClient: HttpClient,
    actual val json: Json,
    actual val logger: DoorLogger,
    actual val dbName: String,
){

    companion object {

        class Builder internal constructor(
            val context: Any,
            val endpoint: String,
            val auth: String,
            val nodeId: Long,
            val httpClient: HttpClient,
            val json: Json,
            val logger: DoorLogger,
            val dbName: String,
        ) {

            fun build() : RepositoryConfig{
                return RepositoryConfig(context, endpoint, auth, nodeId, httpClient, json, logger, dbName)
            }

        }

        fun repositoryConfig(
            context: Any,
            endpoint: String,
            auth: String,
            nodeId: Long,
            httpClient: HttpClient,
            logger: DoorLogger = NapierDoorLogger(),
            dbName: String = endpoint,
            json: Json = Json { encodeDefaults = true },
            block: Builder.() -> Unit = {}
        ) : RepositoryConfig {
            val builder = Builder(context, endpoint,auth, nodeId, httpClient, json, logger, dbName)
            block(builder)
            return builder.build()
        }
    }
}