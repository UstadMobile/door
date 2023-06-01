package com.ustadmobile.door.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a replication that needs to be sent to another node.
 */
@Entity
class OutgoingReplication(
    @PrimaryKey(autoGenerate = true)
    var orUid: Long = 0,
    var destNodeId: Long = 0,
    var orTableId: Long = 0,
    var orPk1: Long = 0,
    var orPk2: Long = 0,
) {
}