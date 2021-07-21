package com.ustadmobile.door.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
class DoorNode {

    @PrimaryKey(autoGenerate = true)
    var nodeId: Int = 0

    var auth: String? = null

}