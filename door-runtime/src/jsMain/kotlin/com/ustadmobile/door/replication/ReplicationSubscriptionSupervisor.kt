package com.ustadmobile.door.replication

import com.ustadmobile.door.DoorDatabaseRepository

/**
 * On JS - we don't have any auto-detection of connectivity - assume always connected. Retry is handled by the browser
 */
actual class ReplicationSubscriptionSupervisor actual constructor(
    replicationSubscriptionManager: ReplicationSubscriptionManager,
    repository: DoorDatabaseRepository
){
    init {
        replicationSubscriptionManager.enabled = true
    }
}