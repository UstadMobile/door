package com.ustadmobile.door.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * This entity is used to track change sequence numbers on SQLite. This is necessary because SQLite
 * does not have builtin sequence support like Postgres. Using MAX(column) is also insufficient
 * because replace performs a delete, and then an insert (making the old change sequence number
 * unavailable).
 */
@Entity(indices = [Index(value = ["sCsnNextLocal"]), Index(value =["sCsnNextPrimary"])])
class SqliteChangeSeqNums(
        @PrimaryKey
        var sCsnTableId: Int = 0,

        var sCsnNextLocal: Int = 1,

        var sCsnNextPrimary: Int = 1)