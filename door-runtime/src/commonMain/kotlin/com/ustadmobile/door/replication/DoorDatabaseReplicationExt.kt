package com.ustadmobile.door.replication

import com.ustadmobile.door.DoorDatabase
import kotlinx.serialization.json.JsonArray

//expect suspend fun DoorDatabase.findPendingTrackers(
//    replicationEntityMetaData: ReplicationEntityMetaData,
//    nodeId: Long
//): JsonArray

suspend fun DoorDatabase.sendPendingReplications(
    remoteNodeId: Long,
    remoteEndpoint: String,
    tableId: Int
) {

}

suspend fun DoorDatabase.fetchPendingReplications(
    remoteNodeId: Long,
    remoteEndpoint: String,
    tableId: Int
) {

}


