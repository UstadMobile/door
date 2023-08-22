package com.ustadmobile.door.replication

import com.ustadmobile.door.ext.*
import com.ustadmobile.door.ext.doorWrapper
import com.ustadmobile.door.ext.doorWrapperNodeId
import com.ustadmobile.door.ktor.KtorCallDbAdapter
import com.ustadmobile.door.replication.ServerSentEventsReplicationClient.Companion.EVT_INIT
import com.ustadmobile.door.sse.DoorServerSentEvent
import io.github.aakira.napier.Napier
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

fun Route.replicationRoute(
    adapter: KtorCallDbAdapter<*>
) {
    get("sse") {
        val remoteNodeIdAndAuth = requireRemoteNodeIdAndAuth()

        val db = adapter(call)
        call.response.cacheControl(CacheControl.NoCache(null))
        val channel = Channel<DoorServerSentEvent>(capacity = Channel.UNLIMITED)
        channel.send(DoorServerSentEvent("0", EVT_INIT, db.doorWrapperNodeId.toString()))

        launch {
            //emit any new outgoing events for this node
            db.doorWrapper.nodeEventManager.outgoingEvents.collect {

            }
        }

        try {
            call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                flush()
                for(notification in channel) {
                    write("data: ${notification.stringify()}\n\n")
                    flush()
                    Napier.d(
                        "Sent event id #${notification.id} ${notification.event} to " +
                            "${remoteNodeIdAndAuth.first}",
                        tag = DoorTag.LOG_TAG
                    )
                }
            }
        }catch(e: Exception) {
            //client gone
            Napier.d("SSE channel close for ${remoteNodeIdAndAuth.first}: $e", tag = DoorTag.LOG_TAG)
            channel.close(e)
        }

    }
}