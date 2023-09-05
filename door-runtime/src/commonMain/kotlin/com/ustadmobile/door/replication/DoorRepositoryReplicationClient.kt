package com.ustadmobile.door.replication

import com.ustadmobile.door.DoorConstants
import com.ustadmobile.door.RepositoryConfig
import com.ustadmobile.door.ext.DoorTag
import com.ustadmobile.door.ext.doorWrapperNodeId
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
 * The DoorRepositoryReplicationClient will connect with another Door node via HTTP to:
 *
 *  - send outgoing replications from this node which are destined for the remote node we are connected to
 *  - receive outgoing replications from the other remote node which are destined for this node
 *
 * Replications are sent and received STRICTLY in the order that they were inserted into the OutgoingReplication table.
 *
 * DoorRepositoryReplicationClient relies on the nodeEventManager to know when it needs to query for pending outgoing or
 * incoming replication
 *
 * @param localNodeId - the Id of this node (the node we are running on) - not the id of the remote node on the other side
 * @param httpClient Ktor HTTP Client (MUST be using Kotlinx Json serializer)
 * @param repoEndpointUrl the url of the other node as per RepositoryConfig.endpoint
 * @param scope CoroutineScope for the repository
 * @param nodeEventManager the NodeEventManager for the local database that we will use to watch for pending
 *        incoming/outgoing replication
 * @param onMarkAcknowledgedAndGetNextOutgoingReplications a function that will mark
 * @param retryInterval the auto retry period
 */
class DoorRepositoryReplicationClient(
    private val localNodeId: Long,
    private val httpClient: HttpClient,
    private val repoEndpointUrl: String,
    scope: CoroutineScope,
    private val nodeEventManager: NodeEventManager<*>,
    private val onMarkAcknowledgedAndGetNextOutgoingReplications: OnMarkAcknowledgedAndGetNextOutgoingReplications,
    private val retryInterval: Int = 10_000,
) {

    constructor(
        db: RoomDatabase,
        repositoryConfig: RepositoryConfig,
        scope: CoroutineScope,
        nodeEventManager: NodeEventManager<*>,
        retryInterval: Int,
    ): this (
        localNodeId = db.doorWrapperNodeId,
        httpClient = repositoryConfig.httpClient,
        repoEndpointUrl = repositoryConfig.endpoint,
        scope = scope,
        nodeEventManager = nodeEventManager,
        onMarkAcknowledgedAndGetNextOutgoingReplications = DefaultOnMarkAcknowledgedAndGetNextOutgoingReplications(db),
        retryInterval = retryInterval,
    )

    /**
     * Functional interface that will run one (single) transaction to acknowledge entities received by the remote node
     * and then query for remaining pending replications (if any, up to the batch size)
     */
    interface OnMarkAcknowledgedAndGetNextOutgoingReplications {

        /**
         * @param receivedAck replicated entities that have been acknowledged by the remote node that should be marked
         *        as processed in our database (e.g. deleted from OutgoingReplication).
         * @param nodeId the id of the remote node that we want to get outgoing replications for
         * @param batchSize the maximum number of pending replication entities to return
         */
        suspend operator fun invoke(
            nodeId: Long,
            receivedAck: ReplicationReceivedAck,
            batchSize: Int
        ): List<DoorReplicationEntity>
    }

    class DefaultOnMarkAcknowledgedAndGetNextOutgoingReplications(
        private val db: RoomDatabase,
    ) : OnMarkAcknowledgedAndGetNextOutgoingReplications{
        override suspend fun invoke(
            nodeId: Long,
            receivedAck: ReplicationReceivedAck,
            batchSize: Int
        ): List<DoorReplicationEntity> {
            return db.withDoorTransactionAsync {
                if(receivedAck.replicationUids.isNotEmpty())
                    db.acknowledgeReceivedReplications(nodeId, receivedAck.replicationUids)

                db.selectPendingOutgoingReplicationsByDestNodeId(nodeId, batchSize)
            }
        }
    }


    private val logPrefix = "[DoorRepositoryReplicationClient from=$localNodeId endpoint=$repoEndpointUrl]"

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

    private val remoteNodeId = CompletableDeferred<Long>()

    init {
        //Get the door node id of the remote endpoint.
        scope.launch {
            while(!remoteNodeId.isCompleted && !remoteNodeId.isCancelled) {
                try {
                    val remoteNodeIdReq = httpClient.get {
                        setRepoUrl(repoEndpointUrl, "replication/nodeId")
                    }
                    remoteNodeIdReq.headers[DoorConstants.HEADER_NODE_ID]?.toLong()?.also {
                        remoteNodeId.complete(it)
                    }
                }catch(e: Exception) {
                    if(e !is CancellationException)
                        delay(retryInterval.toLong())
                }
            }
        }

        fetchPendingReplicationsJob = scope.launch {
            runFetchLoop()
        }

        sendPendingReplicationsJob = scope.launch {
            runSendLoop()
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
        sendNotifyChannel.trySend(Unit)
    }

    private suspend fun CoroutineScope.runSendLoop() {
        val remoteNodeIdVal = remoteNodeId.await()

        val outgoingReplicationsToAck = mutableListOf<Long>()
        while(isActive) {
            try {
                if(outgoingReplicationsToAck.isEmpty()) {
                    sendNotifyChannel.receive()
                }

                val outgoingReplications = onMarkAcknowledgedAndGetNextOutgoingReplications(
                    nodeId = remoteNodeIdVal,
                    receivedAck = ReplicationReceivedAck(
                        replicationUids = outgoingReplicationsToAck,
                    ),
                    batchSize = batchSize
                )
                outgoingReplicationsToAck.clear()

                if(outgoingReplications.isNotEmpty()) {
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

                    outgoingReplicationsToAck.addAll(replicationReceivedAck.replicationUids)
                }
            }catch(e: Exception) {
                Napier.d(
                    tag = DoorTag.LOG_TAG,
                    message =  { "$logPrefix exception sending outgoing replications" },
                    throwable = e
                )
                delay(retryInterval.toLong())
            }
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
        sendNotifyChannel.cancel()
        fetchPendingReplicationsJob.cancel()
        collectEventsJob.cancel()
        fetchNotifyChannel.close()
        sendNotifyChannel.close()
        remoteNodeId.cancel()
    }

}