package com.ustadmobile.door.replication

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Simple class that represents a Replicatable entity - this is used when the entity data itself is serialized as part
 * of NodeEventMessage
 */
@Serializable
data class DoorReplicationEntity(
    val tableId: Int,
    val entity: JsonObject
)
