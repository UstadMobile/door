package com.ustadmobile.door.replication

import com.ustadmobile.door.DoorDatabase
import com.ustadmobile.door.DoorDatabaseRepository

//expect suspend fun DoorDatabase.findPendingTrackers(
//    replicationEntityMetaData: ReplicationEntityMetaData,
//    nodeId: Long
//): JsonArray

//

suspend fun DoorDatabaseRepository.sendPendingReplications(
    tableId: Int
) {
    //should return a result object of some kind
}

suspend fun DoorDatabase.fetchPendingReplications(
    tableId: Int
) {
    //should return a result object of some kind
}


