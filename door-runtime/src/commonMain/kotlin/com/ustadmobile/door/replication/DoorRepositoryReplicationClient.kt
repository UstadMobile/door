package com.ustadmobile.door.replication

import com.ustadmobile.door.RepositoryConfig
import com.ustadmobile.door.ext.DoorTag
import com.ustadmobile.door.ext.setRepoUrl
import com.ustadmobile.door.message.DoorMessage
import com.ustadmobile.door.nodeevent.NodeEventManagerCommon
import com.ustadmobile.door.room.RoomDatabase
import com.ustadmobile.door.util.systemTimeInMillis
import io.github.aakira.napier.Napier
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
    private val db: RoomDatabase,
    private val config: RepositoryConfig,
    private val scope: CoroutineScope,
    private val nodeEventManager: NodeEventManagerCommon<*>,
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
                    channel.receive() //wait for the invalidation signal
                }

                val entitiesReceivedResponse = config.httpClient.post {
                    setRepoUrl(config, "replication/ackAndGetPendingReplications")
                    contentType(ContentType.Application.Json)
                    setBody(ReplicationReceivedAck(acknowledgementsToSend))
                }
                acknowledgementsToSend.clear()

                val entitiesReceivedMessage: DoorMessage = entitiesReceivedResponse.body()
                nodeEventManager.onIncomingMessageReceived(entitiesReceivedMessage)
                acknowledgementsToSend.addAll(entitiesReceivedMessage.replications.map { it.orUid })
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