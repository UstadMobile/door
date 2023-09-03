package com.ustadmobile.door.message

import com.ustadmobile.door.replication.DoorReplicationEntity
import kotlinx.serialization.Serializable

/**
 * A message that is being sent from one DoorNode to another e.g. a Replication, Invalidation, etc. A message can be
 * used to transmit multiple events at a time (e.g. one message for the events of a particular database transaction).
 *
 * Looks like:
 *  'what': 1, //WHAT_REPLICATION
 *  'replications': [
 *     {
 *       tableId: 42,
 *       orUid: 123,
 *       entity: { .. entity fields }
 *     }
 *   ],
 *  'invalidations': []
 *
 *  Important: Must be serialized ONLY using Kotlinx Serialization (not Gson etc) because DoorReplicationEntity
 *  uses Kotlinx Serialization's own JsonObject.
 *
 * @param fromNode the node id of the node that is sending the message
 * @param toNode the node id of the node that should receive this message
 * @param replications object payload (e.g. entities that are being replicated)
 */
@Serializable
data class DoorMessage(
    val what: Int,
    val fromNode: Long,
    val toNode: Long,
    val replications: List<DoorReplicationEntity>,
) {

    companion object {

        /**
         *
         */
        const val WHAT_REPLICATION = 1


    }

}