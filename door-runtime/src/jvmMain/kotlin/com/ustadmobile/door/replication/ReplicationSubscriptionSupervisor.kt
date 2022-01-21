package com.ustadmobile.door.replication

import com.ustadmobile.door.DoorDatabaseRepository

/**
 * On JVM - we don't have any auto-detection of connectivity - assume always connected
 */
actual class ReplicationSubscriptionSupervisor actual constructor(
    replicationSubscriptionManager: ReplicationSubscriptionManager,
    repository: DoorDatabaseRepository
){
    init {
        replicationSubscriptionManager.enabled = true
    }
}