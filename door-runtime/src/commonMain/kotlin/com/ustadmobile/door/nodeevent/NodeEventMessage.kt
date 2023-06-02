package com.ustadmobile.door.nodeevent

import com.ustadmobile.door.replication.DoorReplicationEntity
import kotlinx.serialization.Serializable

/**
 * A message that is being sent from one DoorNode to another e.g. a Replication, Invalidation, etc. A message can be
 * used to transmit multiple events at a time (e.g. one message for the events of a particular database transaction).
 *
 * Looks like:
 *
 *  'replications': [
 *     {
 *       tableId: 42,
 *       entity: { .. entity fields }
 *     }
 *   ],
 *  'invalidations': []
 *
 * @param fromNode the node id of the node that is sending the message
 * @param toNode the node id of the node that should receive this message
 * @param replications object payload (e.g. entities that are being replicated)
 */
@Serializable
data class NodeEventMessage(
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

        const val KEY_REPLICATION_TABLEID = "tableId"

        const val KEY_REPLICATION_ENTITY = "entity"

    }

}