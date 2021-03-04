package com.ustadmobile.door.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents the sync status of a given table. The lastChangedTime is updated when when the
 * table is changed locally or when an UpdateNotification is received from the server.
 */
@Entity
class TableSyncStatus(
        @PrimaryKey
        var tsTableId: Int = 0,

        /**
         * The most recent time that this table was changed (locally or remotely)
         */
        var tsLastChanged: Long = 0L,

        /**
         * The most recent time that this table has been synced
         */
        var tsLastSynced: Long = 0L)
