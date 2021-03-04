package com.ustadmobile.door

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity
data class DoorDatabaseSyncInfo(@PrimaryKey val pk: Int = 0,
                                var dbNodeId: Int = 0,
                                val dbVersion: Int = 0)
