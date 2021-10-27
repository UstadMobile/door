package com.ustadmobile.door

import com.ustadmobile.door.attachments.AttachmentFilter
import io.ktor.client.*
import okhttp3.OkHttpClient

actual class RepositoryConfig internal constructor(
    actual val context: Any,
    actual val endpoint: String,
    actual val auth: String,
    actual val nodeId: Long,
    actual val httpClient: HttpClient,
    val okHttpClient: OkHttpClient,
    actual val attachmentsDir: String,
    actual val updateNotificationManager: ServerUpdateNotificationManager?,
    actual val useClientSyncManager: Boolean,
    actual val attachmentFilters: List<AttachmentFilter>
) {

    companion object {

        class Builder internal constructor(val context: Any, val endpoint: String, val nodeId: Long,
                                           val auth: String, val httpClient: HttpClient, val okHttpClient: OkHttpClient) {

            var attachmentsDir: String? = null

            var updateNotificationManager: ServerUpdateNotificationManager? = null

            var useClientSyncManager: Boolean = false

            val attachmentFilters = mutableListOf<AttachmentFilter>()

            fun build() : RepositoryConfig{
                val effectiveAttachmentDir = attachmentsDir ?: defaultAttachmentDir(context, endpoint)
                return RepositoryConfig(context, endpoint, auth, nodeId, httpClient, okHttpClient, effectiveAttachmentDir,
                        updateNotificationManager, useClientSyncManager, attachmentFilters.toList())
            }

        }

        fun repositoryConfig(context: Any,
                             endpoint: String,
                             nodeId: Long,
                             auth: String,
                             httpClient: HttpClient,
                             okHttpClient: OkHttpClient,
                             block: Builder.() -> Unit = {}
        ) : RepositoryConfig {
            val builder = Builder(context, endpoint, nodeId, auth, httpClient, okHttpClient)
            block(builder)
            return builder.build()
        }

    }

}