package com.ustadmobile.door

import com.ustadmobile.door.attachments.AttachmentFilter
import com.ustadmobile.door.replication.ReplicationSubscriptionMode
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

    /**
     * If true, the repository will automatically create a replication subscription manager and connect to start
     * replication.
     *
     * The database itself should be initialized addCallback(SyncNodeIdCallback(nodeId))
     */
    val useReplicationSubscription: Boolean

    /**
     * By deafault, replication subscription is enabled when a device is connected, and disabled when a device disconnects
     */
    val replicationSubscriptionMode: ReplicationSubscriptionMode

    /**
     * The nodeId for the local node (not the remote node - which is only discovered after connecting to it). This
     * will match the single row that is in SyncNode.
     */
    val nodeId: Long

    /**
     * A listener that will receive events when a replication connection (e.g. subscription) is initialized. This can be
     * useful to do any setup that might be needed for this node to determine what should be replicated to the remote
     * node.
     */
    val replicationSubscriptionInitListener: ReplicationSubscriptionManager.SubscriptionInitializedListener?

    /**
     * Random auth string known only to the repository server and the device
     */
    val auth: String

}