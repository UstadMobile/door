package com.ustadmobile.door

import com.ustadmobile.door.attachments.AttachmentFilter
import io.ktor.client.*
import okhttp3.OkHttpClient

actual class RepositoryConfig internal constructor(actual val context: Any,
                                                   actual val endpoint: String,
                                                   actual val httpClient: HttpClient,
                                                   val okHttpClient: OkHttpClient,
                                                   actual val attachmentsDir: String,
                                                   actual val updateNotificationManager: ServerUpdateNotificationManager?,
                                                   actual val useClientSyncManager: Boolean,
                                                   actual val attachmentFilters: List<AttachmentFilter>) {

    companion object {

        class Builder internal constructor(val context: Any, val endpoint: String, val httpClient: HttpClient,
                                           val okHttpClient: OkHttpClient) {

            var attachmentsDir: String? = null

            var updateNotificationManager: ServerUpdateNotificationManager? = null

            var useClientSyncManager: Boolean = false

            val attachmentFilters = mutableListOf<AttachmentFilter>()

            fun build() : RepositoryConfig{
                val effectiveAttachmentDir = attachmentsDir ?: defaultAttachmentDir(context, endpoint)
                return RepositoryConfig(context, endpoint, httpClient, okHttpClient, effectiveAttachmentDir,
                        updateNotificationManager, useClientSyncManager, attachmentFilters.toList())
            }

        }

        fun repositoryConfig(context: Any, endpoint: String, httpClient: HttpClient, okHttpClient: OkHttpClient, block: Builder.() -> Unit = {}) : RepositoryConfig {
            val builder = Builder(context, endpoint, httpClient, okHttpClient)
            block(builder)
            return builder.build()
        }

    }

}