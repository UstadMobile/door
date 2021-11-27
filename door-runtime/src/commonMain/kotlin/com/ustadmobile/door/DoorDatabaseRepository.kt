package com.ustadmobile.door

import kotlin.reflect.KClass

/**
 * Common interface that is implemented by any repository. Can be used to get info including
 * the active endpoint, auth, database path and the http client.
 */
interface DoorDatabaseRepository {

    val config: RepositoryConfig

    /**
     * This provides access to the underlying database for this repository. It must be wrapped with
     * The SyncableReadOnlyWrapper if this is a syncable database.
     */
    val db: DoorDatabase

    val dbName: String

    /**
     * Adds a weak reference to the given connectivity listener - useful for RepositoryLoadHelper
     * so it can automatically retry requests when connectivity is restored or when a mirror
     * becomes available.
     */
    fun addWeakConnectivityListener(listener: RepositoryConnectivityListener)

    /**
     *
     */
    fun removeWeakConnectivityListener(listener: RepositoryConnectivityListener)

    var connectivityStatus: Int

    /**
     * This map will be a generated map of table names (e.g. EntityName) to the corresponding TableId
     * for all syncable entities
     */
    val tableIdMap: Map<String, Int>

    /**
     * Add a listener that will be called when entities are received from another device.
     */
    fun <T : Any> addSyncListener(entityClass: KClass<T>, syncListener: SyncListener<T>)

    fun <T: Any> removeSyncListener(entityClass: KClass<T>, syncListener: SyncListener<T>)

    /**
     * This function is called by generated code to trigger the SyncListener onSyncEntitiesReceived event. It should
     * NOT be called manually.
     */
    fun <T: Any> handleSyncEntitiesReceived(entityClass: KClass<T>, entitiesIncoming: List<T>)

    companion object {

        const val STATUS_CONNECTED = 1

        const val STATUS_DISCONNECTED = 2

        const val DOOR_ATTACHMENT_URI_SCHEME = "door-attachment"

        val DOOR_ATTACHMENT_URI_PREFIX = "$DOOR_ATTACHMENT_URI_SCHEME://"

        val PATH_REPLICATION = "replication"

        val ENDPOINT_SUBSCRIBE_SSE = "subscribe"

        val ENDPOINT_CHECK_PENDING_REPLICATION_TRACKERS = "checkPendingReplicationTrackers"

        val ENDPOINT_RECEIVE_ENTITIES = "receive"

        val ENDPOINT_CHECK_FOR_ENTITIES_ALREADY_RECEIVED = "checkForEntitiesAlreadyReceived"

        val ENDPOINT_FIND_PENDING_REPLICATION_TRACKERS = "findPendingReplicationTrackers"

        val ENDPOINT_FIND_PENDING_REPLICATIONS = "findPendingReplication"

        val ENDPOINT_MARK_REPLICATE_TRACKERS_AS_PROCESSED = "markReplicateTrackersAsProcessed"




    }
}