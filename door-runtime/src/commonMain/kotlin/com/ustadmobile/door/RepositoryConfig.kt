package com.ustadmobile.door

import com.ustadmobile.door.attachments.AttachmentFilter
import com.ustadmobile.door.replication.ReplicationSubscriptionManager
import io.ktor.client.*
import kotlinx.serialization.json.Json

/**
 * Contains the configuration for a repository. It is created via a platform-specific builder that may have additional
 * dependencies on specific platforms.
 */
expect class RepositoryConfig {

    val context: Any

    val endpoint: String

    val httpClient: HttpClient

    val json: Json

    val attachmentsDir: String

    /**
     * If true, the repository will automatically create a replication subscription manager and connect to start
     * replication
     */
    val useReplicationSubscription: Boolean

    val attachmentFilters: List<AttachmentFilter>

    val nodeId: Long

    val replicationSubscriptionInitListener: ReplicationSubscriptionManager.SubscriptionInitializedListener?

    /**
     * Random auth string known only to the repository server and the device
     */
    val auth: String

}