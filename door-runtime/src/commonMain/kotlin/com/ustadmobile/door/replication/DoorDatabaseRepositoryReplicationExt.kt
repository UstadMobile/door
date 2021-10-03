package com.ustadmobile.door.replication

import com.ustadmobile.door.DoorDatabase
import com.ustadmobile.door.DoorDatabaseRepository
import com.ustadmobile.door.DoorDatabaseRepository.Companion.ENDPOINT_CHECK_FOR_ENTITIES_ALREADY_RECEIVED
import com.ustadmobile.door.DoorDatabaseRepository.Companion.ENDPOINT_FIND_PENDING_REPLICATIONS
import com.ustadmobile.door.DoorDatabaseRepository.Companion.ENDPOINT_FIND_PENDING_REPLICATION_TRACKERS
import com.ustadmobile.door.DoorDatabaseRepository.Companion.ENDPOINT_MARK_REPLICATE_TRACKERS_AS_PROCESSED
import com.ustadmobile.door.DoorDatabaseRepository.Companion.ENDPOINT_RECEIVE_ENTITIES
import com.ustadmobile.door.DoorDatabaseRepository.Companion.PATH_REPLICATION
import com.ustadmobile.door.ext.doorDatabaseMetadata
import com.ustadmobile.door.ext.doorNodeIdHeader
import com.ustadmobile.door.ext.withUtf8Charset
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlin.math.max
import kotlin.math.min


suspend fun DoorDatabaseRepository.sendPendingReplications(
    jsonSerializer: Json,
    remoteNodeId: Long,
    tableId: Int
) {
    //should return a result object of some kind

    val dbMetaData = (this as DoorDatabase)::class.doorDatabaseMetadata()
    val dbKClass = dbMetaData.dbClass

    val repEntityMetaData = dbMetaData.requireReplicateEntityMetaData(tableId)

    var offset = 0
    do {
        val pendingReplicationTrackers = db.findPendingReplicationTrackers(dbMetaData, remoteNodeId, tableId, offset)

        val alreadyUpdatedTrackers = config.httpClient.post<String> {
            url {
                takeFrom(this@sendPendingReplications.config.endpoint)
                encodedPath = "${encodedPath}$PATH_REPLICATION/$ENDPOINT_CHECK_FOR_ENTITIES_ALREADY_RECEIVED"
            }

            doorNodeIdHeader(this@sendPendingReplications)
            parameter("tableId", tableId)

            body = TextContent(jsonSerializer.encodeToString(JsonArray.serializer(), pendingReplicationTrackers),
                ContentType.Application.Json.withUtf8Charset())
        }

        val alreadyUpdatedTrackersJsonArr = jsonSerializer.decodeFromString(JsonArray.serializer(), alreadyUpdatedTrackers)

        db.markReplicateTrackersAsProcessed(dbKClass, dbMetaData, alreadyUpdatedTrackersJsonArr, remoteNodeId, tableId)

        offset += (pendingReplicationTrackers.size - min(alreadyUpdatedTrackersJsonArr.size, repEntityMetaData.batchSize))
    }while(pendingReplicationTrackers.size == repEntityMetaData.batchSize)


    do {
        val pendingReplicationToSend = db.findPendingReplications(dbMetaData, remoteNodeId, tableId)
        config.httpClient.put<Unit> {
            url {
                takeFrom(this@sendPendingReplications.config.endpoint)
                encodedPath = "${encodedPath}$PATH_REPLICATION/$ENDPOINT_RECEIVE_ENTITIES"
            }

            parameter("tableId", tableId)
            doorNodeIdHeader(this@sendPendingReplications)

            body = TextContent(jsonSerializer.encodeToString(JsonArray.serializer(), pendingReplicationToSend),
                ContentType.Application.Json.withUtf8Charset())
        }

        val pendingReplicationsProcessed = repEntityMetaData.entityJsonArrayToReplicationTrackSummaryArray(
            pendingReplicationToSend)

        db.markReplicateTrackersAsProcessed(dbKClass, dbMetaData, pendingReplicationsProcessed,
            remoteNodeId, tableId)
    }while(pendingReplicationsProcessed.size == repEntityMetaData.batchSize)
}

suspend inline fun <reified T> DoorDatabaseRepository.put(
    repEndpointName: String,
    tableId: Int,
    block: HttpRequestBuilder.() -> Unit = {}
) : T {
    return config.httpClient.put<T> {
        url {
            takeFrom(this@put.config.endpoint)
            encodedPath = "${encodedPath}$PATH_REPLICATION/$repEndpointName"
        }

        parameter("tableId", tableId)
        doorNodeIdHeader(this@put)
        block()
    }
}

@Suppress("RedundantSuspendModifier", "UNUSED_PARAMETER", "unused")
suspend fun DoorDatabaseRepository.fetchPendingReplications(
    jsonSerializer: Json,
    tableId: Int,
    remoteNodeId: Long
) {
    val dbMetaData = (this as DoorDatabase)::class.doorDatabaseMetadata()
    val dbKClass = dbMetaData.dbClass

    val repEntityMetaData = dbMetaData.requireReplicateEntityMetaData(tableId)

    //Get pending trackers from remote
    var offset = 0
    do {
        val remotePendingTrackersStr = config.httpClient.get<String> {
            url {
                takeFrom(this@fetchPendingReplications.config.endpoint)
                encodedPath = "$encodedPath${PATH_REPLICATION}/$ENDPOINT_FIND_PENDING_REPLICATION_TRACKERS"
            }

            parameter("tableId", tableId)
            parameter("offset", offset)
            doorNodeIdHeader(this@fetchPendingReplications)
        }

        val remotePendingTrackersJsonArray = jsonSerializer.decodeFromString(JsonArray.serializer(),
            remotePendingTrackersStr)

        val alreadyUpdatedTrackers = db.checkPendingReplicationTrackers(dbKClass, dbMetaData,
            remotePendingTrackersJsonArray, tableId)

        config.httpClient.takeIf { alreadyUpdatedTrackers.isNotEmpty() }?.put<Unit> {
            url {
                takeFrom(this@fetchPendingReplications.config.endpoint)
                encodedPath = "$encodedPath$PATH_REPLICATION/$ENDPOINT_MARK_REPLICATE_TRACKERS_AS_PROCESSED"
            }

            parameter("tableId", tableId)
            doorNodeIdHeader(this@fetchPendingReplications)

            body = TextContent(jsonSerializer.encodeToString(JsonArray.serializer(), alreadyUpdatedTrackers),
                ContentType.Application.Json.withUtf8Charset())
        }

        offset = max(0, (offset + repEntityMetaData.batchSize) - alreadyUpdatedTrackers.size)
    }while(remotePendingTrackersJsonArray.size == repEntityMetaData.batchSize)

    do {
        val pendingReplicationsStr = config.httpClient.get<String> {
            url {
                takeFrom(this@fetchPendingReplications.config.endpoint)
                encodedPath = "$encodedPath$PATH_REPLICATION/$ENDPOINT_FIND_PENDING_REPLICATIONS"
            }

            parameter("tableId", tableId)
            doorNodeIdHeader(this@fetchPendingReplications)
        }

        val pendingReplicationsJson = jsonSerializer.decodeFromString(JsonArray.serializer(), pendingReplicationsStr)

        db.insertReplicationsIntoReceiveView(dbMetaData, dbKClass, remoteNodeId, tableId, pendingReplicationsJson)

        val replicationTrackersToMarkProcessed = repEntityMetaData.entityJsonArrayToReplicationTrackSummaryArray(
            pendingReplicationsJson)

        put<Unit>(ENDPOINT_MARK_REPLICATE_TRACKERS_AS_PROCESSED, tableId) {
            body = TextContent(jsonSerializer.encodeToString(JsonArray.serializer(), replicationTrackersToMarkProcessed),
                ContentType.Application.Json.withUtf8Charset())
        }

    }while(pendingReplicationsJson.size == repEntityMetaData.batchSize)
}
