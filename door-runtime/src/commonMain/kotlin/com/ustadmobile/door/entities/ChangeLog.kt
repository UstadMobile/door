package com.ustadmobile.door.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class ChangeLog (

        @PrimaryKey(autoGenerate = true)
        val changeLogUid: Long = 0,

        val chTableId: Int = 0,

        val chEntityPk: Long = 0L,

        val dispatched: Boolean = false,

        val chTime: Long = 0L

)