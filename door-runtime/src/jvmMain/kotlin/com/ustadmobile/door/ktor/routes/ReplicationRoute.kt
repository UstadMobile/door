package com.ustadmobile.door.ktor.routes

import com.ustadmobile.door.DoorConstants.HEADER_NODE_ID
import com.ustadmobile.door.ext.*
import com.ustadmobile.door.ext.doorWrapper
import com.ustadmobile.door.ext.doorWrapperNodeId
import com.ustadmobile.door.ktor.KtorCallDbAdapter
import com.ustadmobile.door.message.DoorMessage
import com.ustadmobile.door.replication.ReplicationReceivedAck
import com.ustadmobile.door.replication.ServerSentEventsReplicationClient.Companion.EVT_INIT
import com.ustadmobile.door.replication.ServerSentEventsReplicationClient.Companion.EVT_PENDING_REPLICATION
import com.ustadmobile.door.replication.acknowledgeReceivedReplicationsAndSelectNextPendingBatch
import com.ustadmobile.door.sse.DoorServerSentEvent
import com.ustadmobile.door.util.systemTimeInMillis
import io.github.aakira.napier.Napier
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.flow.filter
import kotlinx.serialization.json.Json
import java.io.Writer


fun Route.ReplicationRoute(
    json: Json,
    localNodeId: Long,
    adapter: KtorCallDbAdapter<*>,
) {
    fun Writer.writeDoorEvent(event: DoorServerSentEvent) {
        write("data: ${event.stringify()}\n\n")
        flush()
    }

    /**
     * Server Sent Events endpoint that will emit an SSE event whenever there is a pending NodeEvent for the connected
     * node. When there is a new pending replication, it will emit the server's current time. The client will then
     * call ackAndGetPendingReplications until it has received all pending replications.
     */
    get("sse") {
        val remoteNodeIdAndAuth = requireRemoteNodeIdAndAuth()

        val db = adapter(call)
        try {
            call.response.cacheControl(CacheControl.NoCache(null))
            call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                writeDoorEvent(DoorServerSentEvent("0", EVT_INIT, db.doorWrapperNodeId.toString()))

                db.doorWrapper.nodeEventManager.outgoingEvents
                    .filter { evt ->
                        evt.any { it.toNode == remoteNodeIdAndAuth.first }
                    }.collect {
                        writeDoorEvent(DoorServerSentEvent("0", EVT_PENDING_REPLICATION, systemTimeInMillis().toString()))
                    }
            }
        }catch(e: Exception) {
            Napier.v(
                tag = DoorTag.LOG_TAG,
                message =  { "SSE client gone away ${e.message} - this is probably normal" }
            )
        }
    }

    /**
     * Handle a client acknowledging entities being received and respond with next batch of outgoing replications for
     * this client. See
     * RoomDatabase#acknowledgeReceivedReplicationsAndSelectNextPendingBatch
     */
    post("ackAndGetPendingReplications") {
        val receivedAck : ReplicationReceivedAck = json.decodeFromString(
            ReplicationReceivedAck.serializer(), call.receiveText()
        )
        val nodeIdAndAuth = requireRemoteNodeIdAndAuth()
        val remoteNodeId = nodeIdAndAuth.first

        val db = adapter(call)

        val responseMessage = db.acknowledgeReceivedReplicationsAndSelectNextPendingBatch(
            nodeId = remoteNodeId,
            receivedAck = receivedAck,
        )


        if(responseMessage.replications.isNotEmpty()) {
            call.respondText(contentType = ContentType.Application.Json) {
                json.encodeToString(DoorMessage.serializer(), responseMessage)
            }
        }else {
            call.respondBytes(byteArrayOf(), ContentType.Text.Plain, HttpStatusCode.NoContent)
        }
    }

    /**
     * Handle a client submitting replications (e.g. incoming replication)
     */
    post("message") {
        val db = adapter(call)
        val nodeIdAndAuth = requireRemoteNodeIdAndAuth()
        val message = json.decodeFromString(DoorMessage.serializer(), call.receiveText())

        if(message.fromNode != nodeIdAndAuth.first) {
            call.respondBytes(byteArrayOf(), ContentType.Text.Plain, HttpStatusCode.Forbidden)
            return@post
        }

        db.doorWrapper.nodeEventManager.onIncomingMessageReceived(message)
        val receivedAck = ReplicationReceivedAck(
            replicationUids = message.replications.map { it.orUid }
        )

        call.respondText(contentType = ContentType.Application.Json) {
            json.encodeToString(ReplicationReceivedAck.serializer(), receivedAck)
        }
    }

    post("nodeId") {
        call.response.header(HEADER_NODE_ID, localNodeId.toString())
        call.respondBytes(
            bytes = byteArrayOf(),
            contentType = ContentType.Text.Plain,
            status = HttpStatusCode.NoContent
        )
    }

}
