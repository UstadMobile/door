package com.ustadmobile.door

import com.ustadmobile.door.attachments.AttachmentFilter
import io.ktor.client.*

/**
 * Contains the configuration for a repository. It is created via a platform-specific builder that may have additional
 * dependencies on specific platforms.
 */
actual class RepositoryConfig {
    actual val context: Any
        get() = TODO("Not yet implemented")
    actual val endpoint: String
        get() = TODO("Not yet implemented")
    actual val httpClient: HttpClient
        get() = TODO("Not yet implemented")
    actual val attachmentsDir: String
        get() = TODO("Not yet implemented")
    actual val updateNotificationManager: ServerUpdateNotificationManager?
        get() = TODO("Not yet implemented")
    actual val useClientSyncManager: Boolean
        get() = TODO("Not yet implemented")
    actual val attachmentFilters: List<AttachmentFilter>
        get() = TODO("Not yet implemented")

}