package com.ustadmobile.door

import com.ustadmobile.door.attachments.AttachmentFilter
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
    actual val attachmentsDir: String,
    actual val updateNotificationManager: ServerUpdateNotificationManager?,
    actual val useReplicationSubscription: Boolean,
    actual val attachmentFilters: List<AttachmentFilter>
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

            var attachmentsDir: String? = null

            var updateNotificationManager: ServerUpdateNotificationManager? = null

            var useClientSyncManager: Boolean = true

            val attachmentFilters = mutableListOf<AttachmentFilter>()

            fun build() : RepositoryConfig{
                val effectiveAttachmentDir = attachmentsDir ?: defaultAttachmentDir(context, endpoint)
                return RepositoryConfig(context, endpoint, auth, nodeId, httpClient, okHttpClient, json,
                        effectiveAttachmentDir,updateNotificationManager, useClientSyncManager,
                    attachmentFilters.toList())
            }

        }

        fun repositoryConfig(context: Any,
                             endpoint: String,
                             nodeId: Long,
                             auth: String,
                             httpClient: HttpClient,
                             okHttpClient: OkHttpClient,
                             json: Json,
                             block: Builder.() -> Unit = {}
        ) : RepositoryConfig {
            val builder = Builder(context, endpoint, nodeId, auth, httpClient, okHttpClient, json)
            block(builder)
            return builder.build()
        }

    }

}