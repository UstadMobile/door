package com.ustadmobile.door.message

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * A message that is being sent from one DoorNode to another e.g. a Replication, Invalidation, etc.
 *
 * @param what indicates what type of message this is e.g. Replication, Invalidation, etc as per constants
 * @param fromNode the node id of the node that is sending the message
 * @param toNode the node id of the node that should receive this message
 * @param payload object payload (e.g. entities that are being replicated)
 */
@Serializable
data class DoorMessage(
    val what: Int,
    val fromNode: Long,
    val toNode: Long,
    val payload: JsonObject,
) {

    companion object {

        /**
         *
         */
        const val REPLICATION = 1

    }

}