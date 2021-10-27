package com.ustadmobile.door.util

/**
 * Generate an id between 0 and maxNodeId. This is implemented at the platform level. On Android
 * this can be done using the advertising id. On JVM this can be done by hasing the network mac
 * addresses.
 *
 * This refers to the NodeId component of the primary key and is NOT related to the unique door node id used
 * in replication
 *
 */
expect fun generateDoorNodeId(maxNodeId: Int): Int
