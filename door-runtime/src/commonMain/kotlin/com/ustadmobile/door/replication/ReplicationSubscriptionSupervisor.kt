package com.ustadmobile.door.replication

import com.ustadmobile.door.DoorDatabaseRepository

/**
 * Platform dependent class which will manage switching replication automatically when connectivity is gained or lost
 */
expect class ReplicationSubscriptionSupervisor(
    replicationSubscriptionManager: ReplicationSubscriptionManager,
    repository: DoorDatabaseRepository
) {

}