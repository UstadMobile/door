package com.ustadmobile.door.roomjdbc.basicdb

import androidx.room.Dao
import androidx.room.Insert

@Dao
abstract class BasicRoomDao {

    @Insert
    abstract fun insert(basicRoomEntity: BasicRoomEntity): Long

}