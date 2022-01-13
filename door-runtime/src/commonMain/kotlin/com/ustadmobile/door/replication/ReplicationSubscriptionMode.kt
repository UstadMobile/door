package com.ustadmobile.door.replication

enum class ReplicationSubscriptionMode {
    /**
     * Replication subscription is automatically enabled when the device is connected, and disabled when disconnected
     */
    AUTO,

    /**
     * Replication subscription is not enabled automatically. replicationSubscriptionManager.enabled must be set
     * manually
     */
    MANUAL
}