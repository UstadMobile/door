package com.ustadmobile.door

import com.ustadmobile.door.attachments.AttachmentFilter
import io.ktor.client.*

/**
 * Contains the configuration for a repository. It is created via a platform-specific builder that may have additional
 * dependencies on specific platforms.
 */
expect class RepositoryConfig {

    val context: Any

    val endpoint: String

    val httpClient: HttpClient

    val attachmentsDir: String

    val updateNotificationManager: ServerUpdateNotificationManager?

    val useClientSyncManager: Boolean

    val attachmentFilters: List<AttachmentFilter>

}