package com.ustadmobile.door.replication

import com.ustadmobile.door.DoorDatabase
import com.ustadmobile.door.ext.*
import com.ustadmobile.door.jdbc.ext.executeQueryAsyncKmp
import com.ustadmobile.door.replication.ReplicationEntityMetaData.Companion.KEY_PRIMARY_KEY
import com.ustadmobile.door.replication.ReplicationEntityMetaData.Companion.KEY_VERSION_ID
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.reflect.KClass

/**
 * Get a list of the replication trackers that are pending for the given remoteNode and given tableId and return as a
 * JSON array
 */
suspend fun DoorDatabase.findPendingReplicationTrackers(
    dbMetaData: DoorDatabaseMetadata<*>,
    remoteNodeId: Long,
    tableId: Int
) : JsonArray {
    val repEntityMetaData = dbMetaData.replicateEntities[tableId] ?: throw IllegalArgumentException("No such table: $tableId")
    return prepareAndUseStatementAsync(repEntityMetaData.findPendingTrackerSql) { stmt ->
        stmt.setLong(1, remoteNodeId)
        stmt.executeQueryAsyncKmp().useResults {
            it.rowsToJsonArray(repEntityMetaData.pendingReplicationFieldTypesMap)
        }
    }
}

/**
 * Go through a list of pending replication trackers (e.g. those received from a remote node) and find those that are
 * already up-to-date here.
 *
 * @return list of pending replication tracker
 */
suspend fun <T: DoorDatabase> DoorDatabase.checkPendingReplicationTrackers(
    dbKClass: KClass<out T>,
    dbMetaData: DoorDatabaseMetadata<*>,
    pendingReplications: JsonArray,
    tableId: Int
) : JsonArray {
    val repEntityMetaData = dbMetaData.replicateEntities[tableId] ?: throw IllegalArgumentException("No such table: $tableId")

    val pendingReplicationObjects = pendingReplications.map { it as JsonObject }

    val alreadyUpdatedEntities = mutableLinkedListOf<JsonObject>()
    withDoorTransactionAsync(dbKClass) { transactionDb ->
        transactionDb.prepareAndUseStatementAsync(repEntityMetaData.findAlreadyUpToDateEntitiesSql) { stmt ->
            pendingReplicationObjects.forEach { pendingRep ->
                stmt.setJsonPrimitive(1, repEntityMetaData.entityPrimaryKeyFieldType,
                    pendingRep.get(KEY_PRIMARY_KEY) as JsonPrimitive)
                stmt.setJsonPrimitive(2, repEntityMetaData.versionIdFieldType,
                    pendingRep.get(KEY_VERSION_ID) as JsonPrimitive)

                stmt.executeQueryAsyncKmp().useResults {
                    if(it.next()) {
                        alreadyUpdatedEntities += it.rowToJsonObject(repEntityMetaData.pendingReplicationFieldTypesMap)
                    }
                }
            }
        }
    }

    return JsonArray(alreadyUpdatedEntities)
}