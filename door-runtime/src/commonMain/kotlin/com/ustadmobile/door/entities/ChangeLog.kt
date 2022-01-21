package com.ustadmobile.door.entities

import androidx.room.Entity

@Entity(primaryKeys = arrayOf("chTableId", "chEntityPk"))
data class ChangeLog (

    val chTableId: Int = 0,

    val chEntityPk: Long = 0L,

    val chType: Int
) {
    companion object {
        const val CHANGE_UPSERT = 1

        const val CHANGE_DELETE = 2
    }
}