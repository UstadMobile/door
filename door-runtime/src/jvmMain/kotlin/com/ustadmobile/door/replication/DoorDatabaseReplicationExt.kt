package com.ustadmobile.door.replication

import com.ustadmobile.door.DoorDatabase
import com.ustadmobile.door.ext.useResults
import com.ustadmobile.door.jdbc.ext.executeQueryAsyncKmp
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

//actual suspend fun DoorDatabase.findPendingTrackers(
//    replicationEntityMetaData: ReplicationEntityMetaData,
//    nodeId: Long
//): JsonArray {
//
//    val pendingTrackerList = mutableListOf<JsonArray>()
//    prepareAndUseStatementAsync(replicationEntityMetaData.findPendingTrackerSql) { stmt ->
//        stmt.setLong(1, nodeId)
//        stmt.executeQueryAsyncKmp().useResults { results ->
//            while(results.next()) {
//                val primaryKey = JsonPrimitive(results.getLong("primaryKey"))
//                val versionIdentifier = JsonPrimitive(results.getLong("versionId"))
//                pendingTrackerList += JsonArray(listOf(primaryKey, versionIdentifier))
//            }
//        }
//    }
//
//    return JsonArray(pendingTrackerList)
//}
