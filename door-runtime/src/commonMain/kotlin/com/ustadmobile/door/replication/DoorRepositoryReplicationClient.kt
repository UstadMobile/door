package com.ustadmobile.door.replication

import com.ustadmobile.door.ext.DoorTag
import com.ustadmobile.door.ext.setRepoUrl
import com.ustadmobile.door.message.DoorMessage
import com.ustadmobile.door.nodeevent.NodeEventManager
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
 */
class DoorRepositoryReplicationClient(
    private val httpClient: HttpClient,
    private val repoEndpointUrl: String,
    scope: CoroutineScope,
    private val nodeEventManager: NodeEventManager<*>,
    private val retryInterval: Int = 10_000,
) {

    @Volatile
    var lastInvalidatedTime = systemTimeInMillis()

    /**
     * The time (as per the other node) that we have most recently received all pending outgoing replications
     */
    @Volatile
    var lastReceiveCompleteTime: Long = 0

    private val fetchPendingReplicationsJob : Job

    private val channel = Channel<Unit>(capacity = 1)

    init {
        fetchPendingReplicationsJob = scope.launch {
            runAcknowledgeInsertLoop()
        }

        channel.trySend(Unit)
    }

    private suspend fun CoroutineScope.runAcknowledgeInsertLoop() {
        val acknowledgementsToSend = mutableListOf<Long>()

        while(isActive) {
            try {
                if(acknowledgementsToSend.isEmpty()) {
                    channel.receive() //wait for the invalidation signal if there is nothing we need to acknowledge
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
     *
     */
    fun invalidate() {
        lastInvalidatedTime = systemTimeInMillis()
        channel.trySend(Unit)
    }

    fun close() {
        fetchPendingReplicationsJob.cancel()
    }

}