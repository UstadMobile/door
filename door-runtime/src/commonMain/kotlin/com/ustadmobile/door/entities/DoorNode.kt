package com.ustadmobile.door.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
class DoorNode {

    @PrimaryKey(autoGenerate = true)
    var nodeId: Long = 0

    var auth: String? = null

    @ColumnInfo(defaultValue = "2")
    var rel: Int = SUBSCRIBED_TO

    companion object {
        /**
         * Rel value to indicate that this DoorNode represents a remote node that we (e.g. local) have subscribed to
         * receiving replication updates
         */
        const val SUBSCRIBED_TO = 1

        /**
         * Rel value to indicate that this DoorNode represents a remote node that we (e.g. local) are acting as a server
         * for, where the remote node is using ReplicationSubscriptionManager to receive updates
         */
        const val SERVER_FOR = 2
    }

}