package com.ustadmobile.door.replication

import com.ustadmobile.door.DoorDatabase
import com.ustadmobile.door.DoorDatabaseRepository
import com.ustadmobile.door.DoorDatabaseRepository.Companion.ENDPOINT_CHECK_FOR_ENTITIES_ALREADY_RECEIVED
import com.ustadmobile.door.DoorDatabaseRepository.Companion.ENDPOINT_FIND_PENDING_REPLICATION_TRACKERS
import com.ustadmobile.door.DoorDatabaseRepository.Companion.ENDPOINT_RECEIVE_ENTITIES
import com.ustadmobile.door.DoorDatabaseRepository.Companion.PATH_REPLICATION
import com.ustadmobile.door.ext.doorDatabaseMetadata
import com.ustadmobile.door.ext.doorNodeIdHeader
import com.ustadmobile.door.ext.withUtf8Charset
import com.ustadmobile.door.replication.ReplicationEntityMetaData.Companion.KEY_PRIMARY_KEY
import com.ustadmobile.door.replication.ReplicationEntityMetaData.Companion.KEY_VERSION_ID
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.reflect.KClass

private fun JsonObject.getOrThrow(key: String): JsonElement {
    return get(key) ?: throw IllegalArgumentException("JsonObject.getOrThrow: no key $key")
}

suspend fun DoorDatabaseRepository.sendPendingReplications(
    jsonSerializer: Json,
    remoteNodeId: Long,
    tableId: Int
) {
    //should return a result object of some kind

    val dbMetaData = (this as DoorDatabase)::class.doorDatabaseMetadata()
    val dbKClass = dbMetaData.dbClass

    val repEntityMetaData = dbMetaData.requireReplicateEntityMetaData(tableId)

    val pendingReplicationTrackers = db.findPendingReplicationTrackers(dbMetaData, remoteNodeId, tableId)

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

    val pendingReplicationsProcessed = JsonArray(pendingReplicationToSend.map {
        val jsonObj = it as JsonObject
        JsonObject(mapOf(
            KEY_PRIMARY_KEY to jsonObj.getOrThrow(repEntityMetaData.entityPrimaryKeyFieldName),
            KEY_VERSION_ID to jsonObj.getOrThrow(repEntityMetaData.entityVersionIdFieldName)))
    })

    db.markReplicateTrackersAsProcessed(dbKClass, dbMetaData, pendingReplicationsProcessed,
        remoteNodeId, tableId)
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
    val remotePendingTrackersStr = config.httpClient.get<String> {
        url {
            takeFrom(this@fetchPendingReplications.config.endpoint)
            encodedPath = "$encodedPath$PATH_REPLICATION/$ENDPOINT_FIND_PENDING_REPLICATION_TRACKERS"
        }

        parameter("tableId", tableId)
        doorNodeIdHeader(this@fetchPendingReplications)
    }

    val remotePendingTrackersJsonArray = jsonSerializer.decodeFromString(JsonArray.serializer(),
        remotePendingTrackersStr)
}


