package com.ustadmobile.door

import com.ustadmobile.door.attachments.AttachmentFilter
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
    actual val attachmentsDir: String,
    actual val useReplicationSubscription: Boolean,
    actual val attachmentFilters: List<AttachmentFilter>
){

    companion object {

        class Builder internal constructor(
            val context: Any,
            val endpoint: String,
            val auth: String,
            val nodeId: Long,
            val httpClient: HttpClient,
            val json: Json
        ) {

            var attachmentsDir: String? = null

            var useReplicationSubscription: Boolean = true

            val attachmentFilters = mutableListOf<AttachmentFilter>()

            fun build() : RepositoryConfig{
                val effectiveAttachmentDir = attachmentsDir ?: defaultAttachmentDir(context, endpoint)
                return RepositoryConfig(context, endpoint, auth, nodeId, httpClient, json, effectiveAttachmentDir,
                    useReplicationSubscription, attachmentFilters.toList())
            }

        }

        fun repositoryConfig(
            context: Any,
            endpoint: String,
            auth: String,
            nodeId: Long,
            httpClient: HttpClient,
            json: Json = Json { encodeDefaults = true },
            block: Builder.() -> Unit = {}
        ) : RepositoryConfig {
            val builder = Builder(context, endpoint,auth, nodeId, httpClient, json)
            block(builder)
            return builder.build()
        }
    }
}