package com.ustadmobile.door.replication

import com.ustadmobile.door.DoorDatabaseRepository

suspend fun DoorDatabaseRepository.sendPendingReplications(
    tableId: Int
) {
    //should return a result object of some kind
}

suspend fun DoorDatabaseRepository.fetchPendingReplications(
    tableId: Int
) {
    //should return a result object of some kind
}


