package com.ustadmobile.door.replication

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Simple class that represents a Replicatable entity - this can be used when it is transmitted
 */
@Serializable
data class DoorReplicationEntity(
    val tableId: Int,
    val entity: JsonObject
)
