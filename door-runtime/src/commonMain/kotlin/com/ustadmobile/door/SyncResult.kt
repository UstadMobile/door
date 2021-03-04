package com.ustadmobile.door

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class SyncResult(
        @PrimaryKey(autoGenerate = true) var srUid: Int = 0,
        var tableId: Int = 0,
        var status: Int = 0,
        var localCsn: Int = 0,
        var remoteCsn: Int = 0,
        var syncType: Int = 0,
        var timestamp: Long = 0,
        var sent: Int = 0,
        var received: Int = 0) {


    companion object {

        const val STATUS_FAILED = 1

        const val STATUS_SUCCESS = 2

    }

}