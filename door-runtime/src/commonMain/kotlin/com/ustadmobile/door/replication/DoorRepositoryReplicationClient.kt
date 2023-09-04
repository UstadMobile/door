package com.ustadmobile.door.replication

import com.ustadmobile.door.ext.DoorTag
import com.ustadmobile.door.ext.setRepoUrl
import com.ustadmobile.door.ext.withDoorTransactionAsync
import com.ustadmobile.door.message.DoorMessage
import com.ustadmobile.door.nodeevent.NodeEventManager
import com.ustadmobile.door.room.RoomDatabase
import com.ustadmobile.door.util.systemTimeInMillis
import io.github.aakira.napier.Napier
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlin.concurrent.Volatile


/**
 * The repository replication client will fetch replications from another node (as per the repository config) using
 * the ackAndGetPendingReplications endpoint.
 *
 * It will also collect the outgoing events flow from nodeEventManager and send messages to the
 *
 * @param localNodeId - the Id of this node (the node we are running on) - not the id of the remote node on the other side
 *
 */
class DoorRepositoryReplicationClient(
    private val localNodeId: Long,
    private val httpClient: HttpClient,
    private val repoEndpointUrl: String,
    scope: CoroutineScope,
    private val nodeEventManager: NodeEventManager<*>,
    private val onGetPendingReplicationsForNode: OnGetPendingReplicationsForNode,
    private val onAcknowledgeReceivedReplications: OnAcknowledgeReceivedReplications,
    private val retryInterval: Int = 10_000,

) {

    interface OnGetPendingReplicationsForNode {
        suspend operator fun invoke(nodeId: Long, batchSize: Int): List<DoorReplicationEntity>
    }

    interface OnAcknowledgeReceivedReplications {
        suspend operator fun invoke(nodeId: Long, receivedAck: ReplicationReceivedAck)
    }

    @Volatile
    var lastInvalidatedTime = systemTimeInMillis()

    /**
     * The time (as per the other node) that we have most recently received all pending outgoing replications
     */
    @Volatile
    var lastReceiveCompleteTime: Long = 0

    private val fetchPendingReplicationsJob : Job

    private val sendPendingReplicationsJob: Job

    private val collectEventsJob: Job

    private val fetchNotifyChannel = Channel<Unit>(capacity = 1)

    private val sendNotifyChannel = Channel<Unit>(capacity = 1)

    private val batchSize = 1000

    @Volatile
    private var remoteNodeId = CompletableDeferred<Long>()

    init {
        fetchPendingReplicationsJob = scope.launch {
            runFetchLoop()
        }

        sendPendingReplicationsJob = scope.launch {
            //runSendLoop()
        }

        collectEventsJob = scope.launch {
            val nodeId = remoteNodeId.await()
            nodeEventManager.outgoingEvents.collect { events ->
                if(events.any { it.toNode == nodeId && it.what == DoorMessage.WHAT_REPLICATION }) {
                    sendNotifyChannel.trySend(Unit)
                }
            }
        }

        fetchNotifyChannel.trySend(Unit)
    }

    private suspend fun CoroutineScope.runSendLoop() {
        val remoteNodeIdVal = remoteNodeId.await()

        var probablyHasMoreOutgoingReplications = true
        while(isActive) {
            if(!probablyHasMoreOutgoingReplications) {
                sendNotifyChannel.receive()
            }

            val outgoingReplications = onGetPendingReplicationsForNode(remoteNodeIdVal, batchSize)
//            db.withDoorTransactionAsync {
//                db.selectPendingOutgoingReplicationsByDestNodeId(remoteNodeIdVal, batchSize)
//            }

            val replicationResponse = httpClient.post {
                setRepoUrl(repoEndpointUrl, "replication/message")
                contentType(ContentType.Application.Json)
                setBody(DoorMessage(
                    what = DoorMessage.WHAT_REPLICATION,
                    fromNode = localNodeId,
                    toNode = remoteNodeIdVal,
                    replications = outgoingReplications,
                ))
            }

            val replicationReceivedAck: ReplicationReceivedAck = replicationResponse.body()
            onAcknowledgeReceivedReplications.invoke(remoteNodeIdVal, replicationReceivedAck)
//            db.withDoorTransactionAsync {
//                db.acknowledgeReceivedReplications(remoteNodeIdVal, replicationReceivedAck.replicationUids)
//            }

            probablyHasMoreOutgoingReplications = outgoingReplications.size == batchSize
        }
    }

    private suspend fun CoroutineScope.runFetchLoop() {
        val acknowledgementsToSend = mutableListOf<Long>()

        while(isActive) {
            try {
                if(acknowledgementsToSend.isEmpty()) {
                    fetchNotifyChannel.receive() //wait for the invalidation signal if there is nothing we need to acknowledge
                }

                val entitiesReceivedResponse = httpClient.post {
                    setRepoUrl(repoEndpointUrl, "replication/ackAndGetPendingReplications")
                    contentType(ContentType.Application.Json)
                    setBody(ReplicationReceivedAck(acknowledgementsToSend))
                }
                acknowledgementsToSend.clear()

                if(entitiesReceivedResponse.status == HttpStatusCode.OK) {
                    val entitiesReceivedMessage: DoorMessage = entitiesReceivedResponse.body()
                    nodeEventManager.onIncomingMessageReceived(entitiesReceivedMessage)
                    acknowledgementsToSend.addAll(entitiesReceivedMessage.replications.map { it.orUid })
                }

                if(entitiesReceivedResponse.status == HttpStatusCode.NoContent) {
                    lastReceiveCompleteTime = entitiesReceivedResponse.responseTime.timestamp // to be 100% sure - would be better to timestamp the transaction
                }
            }catch(e: Exception) {
                Napier.v(
                    tag= DoorTag.LOG_TAG,
                    message = { "DoorRepositoryReplicationClient: exception (probably offline): $e"},
                    throwable = e
                )
                delay(retryInterval.toLong())
            }
        }
    }

    /**
     * This should be called when we get a message from the server that there is new pending replication. That can happen
     * via different mechanisms e.g. server sent events.
     */
    fun invalidate() {
        lastInvalidatedTime = systemTimeInMillis()
        fetchNotifyChannel.trySend(Unit)
    }

    fun close() {
        fetchPendingReplicationsJob.cancel()
    }

}