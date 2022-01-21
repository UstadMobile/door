package com.ustadmobile.door.roomjdbc.basicdb

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
class BasicRoomEntity {

    @PrimaryKey(autoGenerate = true)
    var uid: Long = 0

    var name: String? = null

    var orgId: Int = 1

}