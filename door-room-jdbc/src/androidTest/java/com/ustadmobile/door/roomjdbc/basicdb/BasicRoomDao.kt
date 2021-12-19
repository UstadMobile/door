package com.ustadmobile.door.roomjdbc.basicdb

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
abstract class BasicRoomDao {

    @Insert
    abstract fun insert(basicRoomEntity: BasicRoomEntity): Long

    @Query("SELECT * FROM BasicRoomEntity WHERE uid = :uid")
    abstract fun findByUid(uid: Long): BasicRoomEntity?

}