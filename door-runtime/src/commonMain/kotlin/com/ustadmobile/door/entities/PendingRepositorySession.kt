package com.ustadmobile.door.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * This entity is used to by DoorRepositoryReplicationClient
 */
@Entity
data class PendingRepositorySession(
    @PrimaryKey(autoGenerate = true)
    var rsUid: Long = 0,
    var remoteNodeId: Long = 0,
    var endpointUrl: String? = null
) {
}