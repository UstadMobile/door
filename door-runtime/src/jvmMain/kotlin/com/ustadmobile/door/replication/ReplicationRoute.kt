package com.ustadmobile.door.replication

import com.ustadmobile.door.DoorDatabase
import com.ustadmobile.door.DoorDatabaseRepository.Companion.ENDPOINT_CHECK_FOR_ENTITIES_ALREADY_RECEIVED
import com.ustadmobile.door.DoorDatabaseRepository.Companion.ENDPOINT_FIND_PENDING_REPLICATIONS
import com.ustadmobile.door.DoorDatabaseRepository.Companion.ENDPOINT_FIND_PENDING_REPLICATION_TRACKERS
import com.ustadmobile.door.DoorDatabaseRepository.Companion.ENDPOINT_MARK_REPLICATE_TRACKERS_AS_PROCESSED
import com.ustadmobile.door.DoorDatabaseRepository.Companion.ENDPOINT_RECEIVE_ENTITIES
import com.ustadmobile.door.DoorDatabaseRepository.Companion.ENDPOINT_SUBSCRIBE_SSE
import com.ustadmobile.door.DoorDatabaseRepository.Companion.PATH_REPLICATION
import com.ustadmobile.door.IncomingReplicationEvent
import com.ustadmobile.door.entities.NodeIdAndAuth
import com.ustadmobile.door.ext.*
import com.ustadmobile.door.sse.DoorServerSentEvent
import io.github.aakira.napier.Napier
import io.ktor.server.application.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import org.kodein.di.DI
import org.kodein.di.direct
import org.kodein.di.instance
import org.kodein.di.ktor.closestDI
import org.kodein.di.on
import org.kodein.type.TypeToken
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KClass
import com.ustadmobile.door.ktor.addNodeIdAndAuthCheckInterceptor
import com.ustadmobile.door.util.systemTimeInMillis

@Suppress("BlockingMethodInNonBlockingContext") //Has to be done in Server Sent Events
fun <T: DoorDatabase> Route.doorReplicationRoute(
    typeToken: TypeToken<out T>,
    dbKClass: KClass<out T>,
    jsonSerializer: Json
) {

    addNodeIdAndAuthCheckInterceptor()

    route(PATH_REPLICATION) {
        post(ENDPOINT_CHECK_FOR_ENTITIES_ALREADY_RECEIVED) {
            try {
                val startTime = systemTimeInMillis()
                val tableId = call.request.queryParameters["tableId"]?.toInt() ?: 0
                Napier.d("Replication: $ENDPOINT_CHECK_FOR_ENTITIES_ALREADY_RECEIVED table $tableId : starting")
                val di: DI by closestDI()
                val db: DoorDatabase = di.direct.on(call).Instance(typeToken, tag = DoorTag.TAG_DB)
                val dbMetaData = dbKClass.doorDatabaseMetadata()

                val pendingEntitiesStr = call.receive<String>()
                val pendingEntitiesJsonArr = jsonSerializer.decodeFromString(JsonArray.serializer(), pendingEntitiesStr)
                val alreadyReceivedTrackers = db.checkPendingReplicationTrackers(dbKClass,
                    dbMetaData, pendingEntitiesJsonArr, tableId)

                call.respondText(contentType = ContentType.Application.Json.withUtf8Charset(),
                    text = jsonSerializer.encodeToString(JsonArray.serializer(), alreadyReceivedTrackers))
                Napier.d("Replication: $ENDPOINT_CHECK_FOR_ENTITIES_ALREADY_RECEIVED table $tableId " +
                        ": finished after ${systemTimeInMillis() - startTime}ms")
            }catch(e: Exception) {
                Napier.e("Exception on check entities received", e, tag = DoorTag.LOG_TAG)
                call.respond(HttpStatusCode.InternalServerError, e.toString())
            }

        }

        put(ENDPOINT_RECEIVE_ENTITIES) {
            try {
                val startTime = systemTimeInMillis()
                val di: DI by closestDI()
                val tableId = call.request.queryParameters["tableId"]?.toInt() ?: 0
                Napier.d("Replication: $ENDPOINT_RECEIVE_ENTITIES table $tableId : starting")
                val db: DoorDatabase = di.direct.on(call).Instance(typeToken, tag = DoorTag.TAG_DB)
                val dbMetaData = dbKClass.doorDatabaseMetadata()
                val remoteNodeId = requireRemoteNodeIdAndAuth()

                val receivedEntitiesStr = call.receive<String>()
                val receivedEntitiesJsonArr = jsonSerializer.decodeFromString(JsonArray.serializer(),
                    receivedEntitiesStr)
                db.insertReplicationsIntoReceiveView(dbMetaData, dbKClass, remoteNodeId.first, tableId,
                    receivedEntitiesJsonArr)
                call.respondText(text = "", status = HttpStatusCode.NoContent)
                Napier.d("Replication: $ENDPOINT_RECEIVE_ENTITIES table $tableId : finished " +
                        " after ${systemTimeInMillis() - startTime}ms")
                db.incomingReplicationListenerHelper.fireIncomingReplicationEvent(
                    IncomingReplicationEvent(receivedEntitiesJsonArr, tableId))
            }catch(e: Exception) {
                Napier.e("Exception on receive entities", e, tag = DoorTag.LOG_TAG)
                call.respond(HttpStatusCode.InternalServerError, e.toString())
            }

        }

        put(ENDPOINT_MARK_REPLICATE_TRACKERS_AS_PROCESSED) {
            try {
                val startTime = systemTimeInMillis()
                val tableId = call.request.queryParameters["tableId"]?.toInt() ?: 0
                Napier.d("Replication $ENDPOINT_MARK_REPLICATE_TRACKERS_AS_PROCESSED tableId : $tableId : starting")
                call.response.cacheControl(CacheControl.NoCache(null))
                val di: DI by closestDI()

                val db: DoorDatabase = di.direct.on(call).Instance(typeToken, tag = DoorTag.TAG_DB)
                val dbMetaData = dbKClass.doorDatabaseMetadata()

                val trackersStr = call.receive<String>()
                val trackersJson = jsonSerializer.decodeFromString(JsonArray.serializer(), trackersStr)
                val (remoteNodeId, _) = requireRemoteNodeIdAndAuth()

                db.markReplicateTrackersAsProcessed(dbMetaData.dbClass, dbMetaData, trackersJson,
                    remoteNodeId, tableId)
                call.respondText(text = "", status = HttpStatusCode.NoContent)
                Napier.d("Replication $ENDPOINT_MARK_REPLICATE_TRACKERS_AS_PROCESSED tableId : $tableId : " +
                        "finished after ${systemTimeInMillis() - startTime}ms")
            }catch(e: Exception) {
                Napier.e("Exception on mark replicate trackers as processed", e, tag = DoorTag.LOG_TAG)
                call.respond(HttpStatusCode.InternalServerError, e.toString())
            }
        }


        get(ENDPOINT_FIND_PENDING_REPLICATION_TRACKERS) {
            try {
                val tableId = call.request.queryParameters["tableId"]?.toInt() ?: 0
                val startTime = systemTimeInMillis()
                Napier.d("Replication $ENDPOINT_FIND_PENDING_REPLICATION_TRACKERS tableId : $tableId : starting")
                call.response.cacheControl(CacheControl.NoCache(null))
                val di: DI by closestDI()

                val db: DoorDatabase = di.direct.on(call).Instance(typeToken, tag = DoorTag.TAG_DB)
                val dbMetaData = dbKClass.doorDatabaseMetadata()
                val offset = call.request.queryParameters["offset"]?.toInt()  ?: 0

                val (remoteNodeId, _) = requireRemoteNodeIdAndAuth()

                val pendingTrackers = db.findPendingReplicationTrackers(dbMetaData, remoteNodeId, tableId, offset)
                call.respondText(contentType = ContentType.Application.Json.withUtf8Charset(),
                    text = jsonSerializer.encodeToString(JsonArray.serializer(), pendingTrackers))
                Napier.d("Replication $ENDPOINT_FIND_PENDING_REPLICATION_TRACKERS tableId : $tableId : finished " +
                        "after ${systemTimeInMillis() - startTime}ms")
            }catch(e: Exception) {
                Napier.e("Exception finding pending replication trackers", e, tag = DoorTag.LOG_TAG)
                call.respond(HttpStatusCode.InternalServerError, e.toString())
            }
        }

        get(ENDPOINT_FIND_PENDING_REPLICATIONS) {
            try {
                val startTime = systemTimeInMillis()
                val tableId = call.request.queryParameters["tableId"]?.toInt() ?: 0
                Napier.d("Replication $ENDPOINT_FIND_PENDING_REPLICATIONS tableId : $tableId : starting")

                call.response.cacheControl(CacheControl.NoCache(null))
                val di: DI by closestDI()

                val db: DoorDatabase = di.direct.on(call).Instance(typeToken, tag = DoorTag.TAG_DB)
                val dbMetaData = dbKClass.doorDatabaseMetadata()


                val pendingReplications = db.findPendingReplications(dbMetaData, requireRemoteNodeIdAndAuth().first, tableId)
                call.respondText(contentType = ContentType.Application.Json,
                    text = jsonSerializer.encodeToString(JsonArray.serializer(), pendingReplications))
                Napier.d("Replication $ENDPOINT_FIND_PENDING_REPLICATIONS tableId : $tableId : finished " +
                        "after ${systemTimeInMillis() - startTime}ms")
            }catch(e: Exception) {
                Napier.e("Exception finding pending replication trackers", e, tag = DoorTag.LOG_TAG)
                call.respond(HttpStatusCode.InternalServerError, e.toString())
            }
        }

        get(ENDPOINT_SUBSCRIBE_SSE) {
            call.response.cacheControl(CacheControl.NoCache(null))
            val di: DI by closestDI()
            val db: DoorDatabase = di.direct.on(call).Instance(typeToken, tag = DoorTag.TAG_DB)

            val channel = Channel<DoorServerSentEvent>(capacity = Channel.UNLIMITED)
            val remoteNodeIdAndAuth = requireRemoteNodeIdAndAuth()
            val localNodeIdAndAuth: NodeIdAndAuth = di.on(call).direct.instance()
            val evtIdCounter = AtomicInteger()
            val pendingReplicationListener = ReplicationPendingListener {  repEvt ->
                channel.trySend(repEvt.toDoorServerSentEvent(evtIdCounter.incrementAndGet().toString()))
            }
            try {
                channel.send(DoorServerSentEvent("0", ReplicationSubscriptionManager.EVT_INIT,
                    localNodeIdAndAuth.nodeId.toString()))
                Napier.d("ReplicationRoute SSE: started for ${remoteNodeIdAndAuth.first}")
                db.replicationNotificationDispatcher.addReplicationPendingEventListener(remoteNodeIdAndAuth.first,
                    pendingReplicationListener)

                call.response.header("Cache-Control", "no-cache")
                call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                    flush()
                    for(notification in channel) {
                        write("data: ${notification.stringify()}\n\n")
                        flush()
                        Napier.d("Sent event id #${notification.id} ${notification.event} for data: ${notification.data} to " +
                                "${remoteNodeIdAndAuth.first}", tag = DoorTag.LOG_TAG)
                    }
                }

            }catch(e: Exception) {
                e.printStackTrace()
            }finally {
                db.replicationNotificationDispatcher.removeReplicationPendingEventListener(remoteNodeIdAndAuth.first,
                    pendingReplicationListener)
                Napier.d("ReplicationRoute SSE: ended for ${remoteNodeIdAndAuth.first}")
                channel.close()
            }

        }
    }
}