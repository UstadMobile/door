package com.ustadmobile.door.replication

import com.ustadmobile.door.DoorDatabase
import com.ustadmobile.door.DoorDatabaseRepository
import com.ustadmobile.door.DoorDatabaseRepository.Companion.ENDPOINT_CHECK_FOR_ENTITIES_ALREADY_RECEIVED
import com.ustadmobile.door.DoorDatabaseRepository.Companion.ENDPOINT_FIND_PENDING_REPLICATIONS
import com.ustadmobile.door.DoorDatabaseRepository.Companion.ENDPOINT_FIND_PENDING_REPLICATION_TRACKERS
import com.ustadmobile.door.DoorDatabaseRepository.Companion.ENDPOINT_MARK_REPLICATE_TRACKERS_AS_PROCESSED
import com.ustadmobile.door.DoorDatabaseRepository.Companion.ENDPOINT_RECEIVE_ENTITIES
import com.ustadmobile.door.DoorDatabaseRepository.Companion.PATH_REPLICATION
import com.ustadmobile.door.IncomingReplicationEvent
import com.ustadmobile.door.ext.*
import io.github.aakira.napier.Napier
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlin.math.max
import kotlin.math.min

suspend fun DoorDatabaseRepository.sendPendingReplications(
    jsonSerializer: Json,
    tableId: Int,
    remoteNodeId: Long,
) {
    //should return a result object of some kind
    Napier.d("$this : tableId $tableId : sendPendingReplications - start", tag = DoorTag.LOG_TAG)
    val dbMetaData = (this as DoorDatabase)::class.doorDatabaseMetadata()
    val dbKClass = dbMetaData.dbClass

    val repEntityMetaData = dbMetaData.requireReplicateEntityMetaData(tableId)

    var offset = 0
    do {
        val pendingReplicationTrackers = db.findPendingReplicationTrackers(dbMetaData, remoteNodeId, tableId, offset)
        Napier.d("$this : tableId $tableId : sendPendingReplications - found " +
                "${pendingReplicationTrackers.size} pending trackers", tag = DoorTag.LOG_TAG)

        val alreadyUpdatedTrackers = config.httpClient.post<String> {
            url {
                takeFrom(this@sendPendingReplications.config.endpoint)
                encodedPath = "${encodedPath}$PATH_REPLICATION/$ENDPOINT_CHECK_FOR_ENTITIES_ALREADY_RECEIVED"
            }

            dbVersionHeader(this@sendPendingReplications.db)
            doorNodeIdHeader(this@sendPendingReplications)
            parameter("tableId", tableId)

            body = TextContent(jsonSerializer.encodeToString(JsonArray.serializer(), pendingReplicationTrackers),
                ContentType.Application.Json.withUtf8Charset())
        }

        val alreadyUpdatedTrackersJsonArr = jsonSerializer.decodeFromString(JsonArray.serializer(), alreadyUpdatedTrackers)

        Napier.d("$this : tableId $tableId : sendPendingReplications - marking " +
                "${alreadyUpdatedTrackersJsonArr.size} pending trackers as already processed", tag = DoorTag.LOG_TAG)
        db.markReplicateTrackersAsProcessed(dbKClass, dbMetaData, alreadyUpdatedTrackersJsonArr, remoteNodeId, tableId)

        offset += (pendingReplicationTrackers.size - min(alreadyUpdatedTrackersJsonArr.size, repEntityMetaData.batchSize))
    }while(pendingReplicationTrackers.size == repEntityMetaData.batchSize)


    do {
        val pendingReplicationToSend = db.findPendingReplications(dbMetaData, remoteNodeId, tableId)
        Napier.d("$this : tableId $tableId : sendPendingReplications - sending " +
                "${pendingReplicationToSend.size} entities to remote", tag = DoorTag.LOG_TAG)
        config.httpClient.put<Unit> {
            url {
                takeFrom(this@sendPendingReplications.config.endpoint)
                encodedPath = "${encodedPath}$PATH_REPLICATION/$ENDPOINT_RECEIVE_ENTITIES"
            }

            parameter("tableId", tableId)
            dbVersionHeader(this@sendPendingReplications.db)
            doorNodeIdHeader(this@sendPendingReplications)

            body = TextContent(jsonSerializer.encodeToString(JsonArray.serializer(), pendingReplicationToSend),
                ContentType.Application.Json.withUtf8Charset())
        }

        val pendingReplicationsProcessed = repEntityMetaData.entityJsonArrayToReplicationTrackSummaryArray(
            pendingReplicationToSend)

        Napier.d("$this : tableId $tableId : sendPendingReplications - marking " +
                "${pendingReplicationToSend.size} entities as processed", tag = DoorTag.LOG_TAG)
        db.markReplicateTrackersAsProcessed(dbKClass, dbMetaData, pendingReplicationsProcessed,
            remoteNodeId, tableId)
    }while(pendingReplicationsProcessed.size == repEntityMetaData.batchSize)
    Napier.d("$this : tableId $tableId : sendPendingReplications - done", tag = DoorTag.LOG_TAG)
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
        dbVersionHeader(this@put.db)
        block()
    }
}

@Suppress("RedundantSuspendModifier", "UNUSED_PARAMETER", "unused")
suspend fun DoorDatabaseRepository.fetchPendingReplications(
    jsonSerializer: Json,
    tableId: Int,
    remoteNodeId: Long
) {
    Napier.d("$this : tableId $tableId : fetchPendingReplications - start", tag = DoorTag.LOG_TAG)
    val dbMetaData = (this as DoorDatabase)::class.doorDatabaseMetadata()
    val dbKClass = dbMetaData.dbClass

    val repEntityMetaData = dbMetaData.requireReplicateEntityMetaData(tableId)

    //Get pending trackers from remote
    var offset = 0
    var pendingTrackerCount = 0

    do {
        val remotePendingTrackersStr = config.httpClient.get<String> {
            url {
                takeFrom(this@fetchPendingReplications.config.endpoint)
                encodedPath = "$encodedPath${PATH_REPLICATION}/$ENDPOINT_FIND_PENDING_REPLICATION_TRACKERS"
            }

            parameter("tableId", tableId)
            parameter("offset", offset)
            doorNodeIdHeader(this@fetchPendingReplications)
            dbVersionHeader(this@fetchPendingReplications.db)
        }

        val remotePendingTrackersJsonArray = jsonSerializer.decodeFromString(JsonArray.serializer(),
            remotePendingTrackersStr)

        Napier.d("$this : tableId $tableId : fetchPendingReplications - received pending trackers - " +
                "${remotePendingTrackersJsonArray.size} trackers from remote", tag = DoorTag.LOG_TAG)

        val alreadyUpdatedTrackers = db.checkPendingReplicationTrackers(dbKClass, dbMetaData,
            remotePendingTrackersJsonArray, tableId)

        Napier.d("$this : tableId $tableId : fetchPendingReplications - check already updated - " +
                "${alreadyUpdatedTrackers.size} are already updated here", tag = DoorTag.LOG_TAG)

        config.httpClient.takeIf { alreadyUpdatedTrackers.isNotEmpty() }?.put<Unit> {
            url {
                takeFrom(this@fetchPendingReplications.config.endpoint)
                encodedPath = "$encodedPath$PATH_REPLICATION/$ENDPOINT_MARK_REPLICATE_TRACKERS_AS_PROCESSED"
            }

            parameter("tableId", tableId)
            doorNodeIdHeader(this@fetchPendingReplications)
            dbVersionHeader(this@fetchPendingReplications.db)

            body = TextContent(jsonSerializer.encodeToString(JsonArray.serializer(), alreadyUpdatedTrackers),
                ContentType.Application.Json.withUtf8Charset())
        }

        offset = max(0, (offset + repEntityMetaData.batchSize) - alreadyUpdatedTrackers.size)
        pendingTrackerCount += (remotePendingTrackersJsonArray.size - alreadyUpdatedTrackers.size)
    }while(remotePendingTrackersJsonArray.size == repEntityMetaData.batchSize)

    if(pendingTrackerCount == 0)
        return //nothing to do

    do {
        val pendingReplicationsStr = config.httpClient.get<String> {
            url {
                takeFrom(this@fetchPendingReplications.config.endpoint)
                encodedPath = "$encodedPath$PATH_REPLICATION/$ENDPOINT_FIND_PENDING_REPLICATIONS"
            }

            parameter("tableId", tableId)
            doorNodeIdHeader(this@fetchPendingReplications)
            dbVersionHeader(this@fetchPendingReplications.db)
        }

        val pendingReplicationsJson = jsonSerializer.decodeFromString(JsonArray.serializer(), pendingReplicationsStr)
        Napier.d("$this : tableId $tableId : fetchPendingReplications - received - " +
                "${pendingReplicationsJson.size} entities from remote", tag = DoorTag.LOG_TAG)

        db.insertReplicationsIntoReceiveView(dbMetaData, dbKClass, remoteNodeId, tableId, pendingReplicationsJson)
        Napier.d("$this : tableId $tableId : fetchPendingReplications - received - " +
                "${pendingReplicationsJson.size} entities inserted into receive view", tag = DoorTag.LOG_TAG)
        db.incomingReplicationListenerHelper.fireIncomingReplicationEvent(
            IncomingReplicationEvent(pendingReplicationsJson, tableId))

        val replicationTrackersToMarkProcessed = repEntityMetaData.entityJsonArrayToReplicationTrackSummaryArray(
            pendingReplicationsJson)

        put<Unit>(ENDPOINT_MARK_REPLICATE_TRACKERS_AS_PROCESSED, tableId) {
            body = TextContent(jsonSerializer.encodeToString(JsonArray.serializer(), replicationTrackersToMarkProcessed),
                ContentType.Application.Json.withUtf8Charset())
        }
        Napier.d("$this : tableId $tableId : fetchPendingReplications - marked as processed - " +
                "${replicationTrackersToMarkProcessed.size} trackers", tag = DoorTag.LOG_TAG)

    }while(pendingReplicationsJson.size == repEntityMetaData.batchSize)
    Napier.d("$this : tableId $tableId : fetchPendingReplications - done", tag = DoorTag.LOG_TAG)
}

/**
 * This is used by generated code to create a replicationSubscriptionManager if needed.
 */
@Suppress("Unused") //This is used by generated code
fun DoorDatabaseRepository.makeNewSubscriptionManager(
    coroutineScope: CoroutineScope = GlobalScope
): ReplicationSubscriptionManager {
    val dbMetadata = this.db::class.doorDatabaseMetadata()
    return ReplicationSubscriptionManager(dbMetadata.version, config.json, db.replicationNotificationDispatcher,
        this, coroutineScope, dbMetadata, dbMetadata.dbClass,
        onSubscriptionInitialized = config.replicationSubscriptionInitListener)
}

