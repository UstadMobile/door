package com.ustadmobile.door.nodeevent

import com.ustadmobile.door.DoorConstants
import com.ustadmobile.door.RepositoryConfig
import com.ustadmobile.door.ext.DoorTag
import com.ustadmobile.door.ext.urlEncode
import com.ustadmobile.door.message.DoorMessage
import com.ustadmobile.door.replication.ServerSentEventsReplicationClient.Companion.EVT_INIT
import com.ustadmobile.door.replication.ServerSentEventsReplicationClient.Companion.EVT_PENDING_REPLICATION
import com.ustadmobile.door.sse.DoorEventListener
import com.ustadmobile.door.sse.DoorEventSource
import com.ustadmobile.door.sse.DoorServerSentEvent
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile

class NodeEventSseClient(
    private val repoConfig: RepositoryConfig,
    private val nodeEventManager: NodeEventManager<*>,
    private val scope: CoroutineScope,
) : DoorEventListener{

    private val eventSource: DoorEventSource

    private val url = "${repoConfig.endpoint}replication/sse?${DoorConstants.HEADER_NODE_AND_AUTH}=" +
            "${repoConfig.nodeId}/${repoConfig.auth}".urlEncode()

    private val logPrefix: String = "[NodeEventSseClient localNodeId=${repoConfig.nodeId} remoteEndpoint=${repoConfig.endpoint}]"

    @Volatile
    private var remoteNodeId: Long = 0

    @Volatile
    private var isClosed = false

    init {
        eventSource = DoorEventSource(repoConfig, url, this)
    }

    override fun onOpen() {
        Napier.v(tag = DoorTag.LOG_TAG) {
            "$logPrefix : open"
        }
    }

    override fun onMessage(message: DoorServerSentEvent) {
        when(message.event) {
            EVT_INIT -> {
                remoteNodeId = message.data.toLong()
                Napier.v(tag = DoorTag.LOG_TAG) {
                    "$logPrefix : onMessage : INIT: remoteNodeId = $remoteNodeId"
                }
            }

            EVT_PENDING_REPLICATION -> {
                Napier.v(tag = DoorTag.LOG_TAG) {
                    "$logPrefix : onMessage : pending replication"
                }

                scope.launch {
                    nodeEventManager.onIncomingMessageReceived(DoorMessage(
                        what = DoorMessage.WHAT_REPLICATION,
                        fromNode = remoteNodeId,
                        toNode = repoConfig.nodeId,
                        replications = emptyList()
                    ))
                }
            }
        }
    }

    override fun onError(e: Exception) {
        if(!isClosed) { //When closed there will always be a socket close exception which is just noise in the logs
            Napier.w(tag = DoorTag.LOG_TAG, throwable = e) {
                "$logPrefix : onError"
            }
        }
    }

    fun close() {
        Napier.v(tag = DoorTag.LOG_TAG) {
            "$logPrefix : close"
        }

        try {
            isClosed = true
            eventSource.close()
        }catch(e: Exception) {
            Napier.w(tag = DoorTag.LOG_TAG, throwable = e) {
                "$logPrefix : exception closing"
            }
        }
    }



}