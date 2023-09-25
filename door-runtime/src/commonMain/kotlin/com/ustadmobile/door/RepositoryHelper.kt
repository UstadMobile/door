package com.ustadmobile.door

import com.ustadmobile.door.ext.concurrentSafeListOf
import com.ustadmobile.door.ext.doorWrapper
import com.ustadmobile.door.nodeevent.NodeEventManager
import com.ustadmobile.door.nodeevent.NodeEventSseClient
import com.ustadmobile.door.replication.DoorRepositoryReplicationClient
import com.ustadmobile.door.room.RoomDatabase
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow

/**
 * The RepositoryHelper has common implementation logic needed by repositories. This can't be done using inheritance
 * because the superclass is the Database itself. It hosts the DoorRepositoryReplicationClient,
 *
 * @param db The underlying database: MUST be the DoorWrapper version
 * @param repoConfig the RepositoryConfig being used (that should contain the endpoint etc).
 */
class RepositoryHelper(
    private val db: RoomDatabase,
    private val repoConfig: RepositoryConfig,
) {

    private val connectivityStatusAtomic = atomic(0)

    private val connectivityListeners: MutableList<RepositoryConnectivityListener> = concurrentSafeListOf()

    val scope = CoroutineScope(Dispatchers.Default + Job())

    private val nodeEventManager: NodeEventManager<*> = db.doorWrapper.nodeEventManager

    private val client = DoorRepositoryReplicationClient(
        db = db,
        repositoryConfig = repoConfig,
        scope = scope,
        nodeEventManager = nodeEventManager,
        retryInterval = 1_000 //This could/should be added to repositoryconfig
    )

    private val eventClient = NodeEventSseClient(repoConfig, nodeEventManager, scope)

    val clientState: Flow<DoorRepositoryReplicationClient.ClientState>
        get() = client.state

    var connectivityStatus: Int
        get() = connectivityStatusAtomic.value
        set(newValue) {
            connectivityStatusAtomic.value = newValue
            connectivityListeners.forEach {
                try {
                    /**
                     * There could be repo-backed livedata that is waiting to try and re-run.
                     * This might cause exceptions if the server itself is off even if connectivity
                     * is back, or if the connectivity is not great. Hence this is wrapped in
                     * try-catch
                     */
                    it.onConnectivityStatusChanged(newValue)
                }catch(e: Exception) {
                    println("Exception with weakConnectivityListener $e")
                }
            }
        }

    fun remoteNodeIdOrNull() : Long? {
        return client.remoteNodeIdOrNull()
    }

    fun remoteNodeIdOrFake(): Long {
        return client.remoteNodeIdOrFake()
    }


    fun close() {
        client.close()
        eventClient.close()
        scope.cancel()
    }

}