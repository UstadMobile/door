package com.ustadmobile.door.entities

import androidx.room.Entity

/**
 * This class will be inserted when replication is being handled from a particular node / table id if this entity
 * (ReplicationOperation) is included in the Database itself. This will happen whenever the entity remote insert strategy
 * is running (both incoming pushes and pulls from other nodes).
 *
 * This can be used by triggers to avoid sending creating OutgoingReplication updates that go back directly to the node
 * that just sent us the change.
 *
 * It will be deleted once the inserts (e.g. into the receive view or direct, as per the annotation on the ReplicateEntity)
 * are done.
 */
@Entity(primaryKeys = arrayOf("repOpRemoteNodeId", "repOpTableId"))
data class ReplicationOperation(
    var repOpRemoteNodeId: Long = 0,
    var repOpTableId: Int = 0,
    var repOpStatus: Int = 0,
)
