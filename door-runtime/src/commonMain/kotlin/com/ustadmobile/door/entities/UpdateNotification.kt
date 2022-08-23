package com.ustadmobile.door.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(indices = [Index(value = ["pnDeviceId", "pnTableId"], unique = true),
        Index(value = ["pnDeviceId", "pnTimestamp"], unique = false)])
class UpdateNotification(
        @PrimaryKey(autoGenerate = true)
        var pnUid: Long = 0,
        var pnDeviceId: Int = 0,
        var pnTableId: Int = 0,
        var pnTimestamp: Long = 0) {

        override fun toString(): String {
                return "UpdateNotification: pnUid=$pnUid pnDeviceId=$pnDeviceId tableId=$pnTableId pnTimestamp=$pnTimestamp"
        }
}