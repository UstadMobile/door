package com.ustadmobile.door.replication

import kotlinx.serialization.Serializable


/**
 * This entity is sent from one node to another node after it has received replication entities. It contains a list of
 * the OutgoingReplication orUids that have been processed.
 */
@Serializable
data class ReplicationReceivedAck(
    val replicationUids: List<Long>
)
