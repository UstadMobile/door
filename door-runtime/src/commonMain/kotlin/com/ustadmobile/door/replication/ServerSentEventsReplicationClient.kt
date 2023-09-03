package com.ustadmobile.door.replication

import com.ustadmobile.door.DoorDatabaseWrapper
import com.ustadmobile.door.RepositoryConfig
import com.ustadmobile.door.room.RoomDatabase
import com.ustadmobile.door.sse.DoorEventListener
import com.ustadmobile.door.sse.DoorEventSource
import com.ustadmobile.door.sse.DoorServerSentEvent
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Replication client based on the use of Server Sent Events. Might use a common base class (e.g. AbstractReplicationClient)
 *
 */
class ServerSentEventsReplicationClient(
    repositoryConfig: RepositoryConfig,
    private val doorWrappedDb:  RoomDatabase,
    scope: CoroutineScope? = null,
): DoorEventListener {

    private val replicationScope = scope ?: CoroutineScope(Dispatchers.Default + Job())

    private val remoteNodeId = atomic(0L)

    private val eventSource: AtomicRef<DoorEventSource?> = atomic(null)

    init {
        val evtSource = DoorEventSource(
            repoConfig = repositoryConfig,
            url = "${repositoryConfig.endpoint}replication/sse",
            listener = this,
        )

        eventSource.value = evtSource
    }


    override fun onOpen() {

    }

    override fun onMessage(message: DoorServerSentEvent) {
        if(message.event == EVT_INIT) {
            val nodeId = message.data.toLong()
            remoteNodeId.value = nodeId
            replicationScope.launch {
                //send any pending replications for this node
            }


            replicationScope.launch {
                (doorWrappedDb as DoorDatabaseWrapper<*>).nodeEventManager.outgoingEvents.map { evtList ->
                    evtList.filter { it.toNode == nodeId }
                }.filter {
                    it.isNotEmpty()
                }.collect {
                    //new pending replication for this node - send them
                }
            }
        }
    }

    override fun onError(e: Exception) {

    }

    fun close() {

    }


    companion object {

        const val EVT_INIT = "init"

        const val EVT_PENDING_REPLICATION = "pending-replication"

    }
}