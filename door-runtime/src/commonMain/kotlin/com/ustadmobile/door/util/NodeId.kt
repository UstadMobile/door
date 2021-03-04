package com.ustadmobile.door.util

/**
 * Generate an id between 0 and maxNodeId. This is implemented at the platform level. On Android
 * this can be done using the advertising id. On JVM this can be done by hasing the network mac
 * addresses.
 */
expect fun generateDoorNodeId(maxNodeId: Int): Int
