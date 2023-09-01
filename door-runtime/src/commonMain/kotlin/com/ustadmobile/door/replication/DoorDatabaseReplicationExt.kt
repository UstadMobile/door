package com.ustadmobile.door.replication

import com.ustadmobile.door.room.RoomDatabase
import com.ustadmobile.door.entities.DoorNode
import com.ustadmobile.door.ext.*
import com.ustadmobile.door.jdbc.ext.*
import com.ustadmobile.door.message.DoorMessage
import com.ustadmobile.door.nodeevent.NodeEvent


/**
 * Select the given replicate entities as a JSON array. (e.g. that can be sent out as a replication message).
 * document on door message
 *
 */
suspend fun RoomDatabase.selectNodeEventMessageReplications(
    events: Iterable<NodeEvent>,
): List<DoorReplicationEntity> {
    return events.runningSplitBy { it.tableId }.map { tableEvents ->
        val tableId = tableEvents.first().tableId
        val entityMetaData = this::class.doorDatabaseMetadata().requireReplicateEntityMetaData(tableId)
        val entityFieldsTypeMap = entityMetaData.entityFieldsTypeMap

        prepareAndUseStatementAsync(entityMetaData.selectEntityByPrimaryKeysSql) { stmt ->
            tableEvents.mapNotNull { event ->
                stmt.setLong(1, event.key1)
                stmt.executeQueryAsyncKmp().useResults { result ->
                    result.mapNextRow(null) { mapResult ->
                        DoorReplicationEntity(
                            tableId = tableId,
                            entity = mapResult.rowToJsonObject(entityFieldsTypeMap),
                        )
                    }
                }
            }
        }
    }.flatten()
}

/**
 *
 */
suspend fun RoomDatabase.insertEntitiesFromMessage(
    message: DoorMessage,
) {
    message.replications.runningSplitBy { it.tableId }.forEach { tableEntities ->
        val tableId = tableEntities.first().tableId
        val entityMetaData = this::class.doorDatabaseMetadata().requireReplicateEntityMetaData(tableId)

        prepareAndUseStatementAsync(entityMetaData.insertIntoReceiveViewSql) { stmt ->
            tableEntities.forEach { entity ->
                stmt.setAllFromJsonObject(entity.entity, entityMetaData.entityFields)

                //Set the fromNodeId, which is always last
                stmt.setLong(entityMetaData.entityFields.size + 1, message.fromNode)
                stmt.executeUpdateAsyncKmp()
            }
        }
    }
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
