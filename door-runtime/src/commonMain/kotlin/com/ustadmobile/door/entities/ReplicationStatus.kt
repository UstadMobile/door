package com.ustadmobile.door.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(indices = arrayOf(Index("tableId", "nodeId", name="table_node_idx", unique = true)))
class ReplicationStatus {

    @PrimaryKey(autoGenerate = true)
    var repStatusId: Int = 0

    var tableId: Int = 0

    var priority: Int = 100

    var nodeId: Long = 0

    var lastRemoteChangeTime: Long = 0

    var lastFetchReplicationCompleteTime: Long = 0

    var lastLocalChangeTime: Long = 0

    var lastSendReplicationCompleteTime: Long = 0

}