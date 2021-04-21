package com.ustadmobile.door

import com.ustadmobile.door.attachments.AttachmentFilter
import io.ktor.client.*

/**
 * Contains the configuration for a repository. It is created via a platform-specific builder that may have additional
 * dependencies on specific platforms.
 */
actual class RepositoryConfig internal constructor(actual val context: Any, actual val endpoint: String,
                                                   actual val httpClient: HttpClient,
                                                   actual val attachmentsDir: String,
                                                   actual val updateNotificationManager: ServerUpdateNotificationManager?,
                                                   actual val useClientSyncManager: Boolean,
                                                   actual val attachmentFilters: List<AttachmentFilter>){

}