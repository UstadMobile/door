package com.ustadmobile.door.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Device(
    @PrimaryKey
    val deviceId: Int=  0,

    val deviceKey: String? = null,

    val deviceType: Int = 0,

    val osVersion: String? = null
)