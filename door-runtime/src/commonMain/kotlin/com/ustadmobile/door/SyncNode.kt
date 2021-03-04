package com.ustadmobile.door

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity
@Serializable
data class SyncNode(@PrimaryKey var nodeClientId: Int = 0, var master: Boolean = false)