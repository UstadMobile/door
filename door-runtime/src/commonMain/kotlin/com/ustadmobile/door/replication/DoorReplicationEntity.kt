package com.ustadmobile.door.replication

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Simple class that represents a Replicatable entity - this is used when the entity data itself is serialized as part
 * of NodeEventMessage
 * @param tableId the Table ID of the entity being replicated
 * @param orUid (Outgoing Replication Uid) If this is being sent due to a push (OutgoingReplication) then this is the
 *              OutgoingReplicationUid that needs to be acknowledged.
 *
 *  Important: Must be serialized ONLY using Kotlinx Serialization (not Gson etc) because entity uses Kotlinx
 *  Serialization's own JsonObject
 */
@Serializable
data class DoorReplicationEntity(
    val tableId: Int,
    val orUid: Long,
    val entity: JsonObject
)
