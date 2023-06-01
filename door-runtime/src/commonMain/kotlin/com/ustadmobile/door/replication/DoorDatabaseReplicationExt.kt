package com.ustadmobile.door.replication

import com.ustadmobile.door.room.RoomDatabase
import com.ustadmobile.door.entities.DoorNode
import com.ustadmobile.door.ext.*
import com.ustadmobile.door.jdbc.ext.executeQueryAsyncKmp
import com.ustadmobile.door.jdbc.ext.executeUpdateAsyncKmp
import com.ustadmobile.door.jdbc.ext.mapRows
import com.ustadmobile.door.jdbc.ext.useResults
import com.ustadmobile.door.replication.ReplicationEntityMetaData.Companion.KEY_PRIMARY_KEY
import com.ustadmobile.door.replication.ReplicationEntityMetaData.Companion.KEY_VERSION_ID
import kotlinx.serialization.json.*

/**
 * Go through a list of pending replication trackers (e.g. those received from a remote node) and find those that are
 * already up-to-date here.
 *
 * @return list of pending replication tracker
 */
suspend fun RoomDatabase.checkPendingReplicationTrackers(
    dbMetaData: DoorDatabaseMetadata<*>,
    pendingReplications: JsonArray,
    tableId: Int
) : JsonArray {
    val repEntityMetaData = dbMetaData.replicateEntities[tableId] ?: throw IllegalArgumentException("No such table: $tableId")

    val pendingReplicationObjects = pendingReplications.map { it as JsonObject }

    val alreadyUpdatedEntities = mutableLinkedListOf<JsonObject>()
    withDoorTransactionAsync { transactionDb ->
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


suspend fun RoomDatabase.insertReplicationsIntoReceiveView(
    dbMetaData: DoorDatabaseMetadata<*>,
    @Suppress("UNUSED_PARAMETER") //This is reserved for future usage (e.g. to set when doing the insert to help with permission checking)
    remoteNodeId: Long,
    tableId: Int,
    receivedEntities: JsonArray
) {
    if(receivedEntities.isEmpty())
        return //do nothing, nothing was received

    val repEntityMetaData = dbMetaData.replicateEntities[tableId] ?: throw IllegalArgumentException("No such table: $tableId")
    val receivedObjects = receivedEntities.map { it as JsonObject }

    /*
     Will be used again after changes
    withDoorTransactionAsync { transactionDb ->
        transactionDb.prepareAndUseStatementAsync(repEntityMetaData.insertIntoReceiveViewSql) { insertStmt ->
            transactionDb.prepareAndUseStatementAsync(repEntityMetaData.insertOrUpdateTrackerSql(dbType())) { updateTrackerStmt ->
                receivedObjects.forEach { receivedObject ->
                    for(i in 0 until repEntityMetaData.insertIntoReceiveViewTypesList.size) {
                        val objFieldVal = (receivedObject.get(repEntityMetaData.insertIntoReceiveViewTypeColNames[i]) as? JsonPrimitive)
                            .toDefaultValIfNull(repEntityMetaData.insertIntoReceiveViewTypesList[i])
                        insertStmt.setJsonPrimitive(i + 1, repEntityMetaData.insertIntoReceiveViewTypesList[i],
                            objFieldVal)
                    }

                    insertStmt.executeUpdateAsyncKmp()

                    val primaryKeyVal = receivedObject.get(repEntityMetaData.entityPrimaryKeyFieldName)?.jsonPrimitive
                        ?: throw IllegalArgumentException("No primary key field value")
                    val entityVersionVal = receivedObject.get(repEntityMetaData.entityVersionIdFieldName)?.jsonPrimitive
                        ?: throw IllegalArgumentException("No entity version field value")

                    updateTrackerStmt.setJsonPrimitive(1, repEntityMetaData.entityPrimaryKeyFieldType, primaryKeyVal)
                    updateTrackerStmt.setJsonPrimitive(2, repEntityMetaData.versionIdFieldType, entityVersionVal)
                    updateTrackerStmt.setLong(3, remoteNodeId)
                    updateTrackerStmt.setJsonPrimitive(4, repEntityMetaData.entityPrimaryKeyFieldType, primaryKeyVal)
                    updateTrackerStmt.setJsonPrimitive(5, repEntityMetaData.versionIdFieldType, entityVersionVal)
                    updateTrackerStmt.executeUpdateAsyncKmp()
                }

            }
        }
    }
     */

}

internal suspend fun RoomDatabase.getDoorNodeAuth(nodeId : Long): String? {
    return prepareAndUseStatementAsync("""SELECT auth
          FROM DoorNode
         WHERE nodeId = ?""") { stmt ->

        stmt.setLong(1, nodeId)

        stmt.executeQueryAsyncKmp().useResults { results ->
            results.mapRows { it.getString(1) }.firstOrNull()
        }
    }
}

internal suspend fun RoomDatabase.insertNewDoorNode(node: DoorNode) {
    prepareAndUseStatementAsync("INSERT INTO DoorNode(nodeId, auth, rel) VALUES(?, ?, ?)") { stmt ->
        stmt.setLong(1, node.nodeId)
        stmt.setString(2, node.auth)
        stmt.setInt(3, node.rel)
        stmt.executeUpdateAsyncKmp()
    }
}

internal suspend fun RoomDatabase.selectDoorNodeExists(nodeId: Long): Boolean {
    return prepareAndUseStatementAsync("""
        SELECT EXISTS(
               SELECT nodeId 
                 FROM DoorNode
                WHERE nodeId = ?) 
    """) { stmt ->
        stmt.setLong(1, nodeId)
        stmt.executeQueryAsyncKmp().useResults { results -> results.mapRows {
            it.getBoolean(1)
        } }.first()
    }
}
