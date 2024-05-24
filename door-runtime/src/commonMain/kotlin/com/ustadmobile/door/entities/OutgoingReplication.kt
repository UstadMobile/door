package com.ustadmobile.door.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a replication that needs to be sent to another node. Replicate Entities MUST have a primary key consisting
 * of one or two Longs. Two longs can be used to store a UUID.
 *
 * Replication is triggered when an insertion is made into the OutgoingReplication table.
 *
 * @param orUid the Primary Key for this table
 * @param destNodeId the nodeId of the node that this outgoingreplication should be delivered to
 * @param orTableId the tableId for this entity as per the tableId on the ReplicateEntity annotation on the Entity class
 * @param orPk1 the Primary Key of the entity to replicate
 * @param orPk2 the second primary key of the entity to replicate (if used, otherwise 0)
 * @param orPk3 the third primary key of the entity to replicate (if used, otherwise 0)
 * @param orPk4 the fourth primary key of the entity to replicate (if used, otherwise 0)
 */
@Entity
class OutgoingReplication(
    @PrimaryKey(autoGenerate = true)
    var orUid: Long = 0,
    var destNodeId: Long = 0,
    var orTableId: Int = 0,
    var orPk1: Long = 0,
    @ColumnInfo(defaultValue = "0")
    var orPk2: Long = 0,
    @ColumnInfo(defaultValue = "0")
    var orPk3: Long = 0,
    @ColumnInfo(defaultValue = "0")
    var orPk4: Long = 0,
)
