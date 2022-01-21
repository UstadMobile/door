package com.ustadmobile.door.roomjdbc.basicdb

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
abstract class BasicRoomDao {

    @Insert
    abstract fun insert(basicRoomEntity: BasicRoomEntity): Long

    @Query("SELECT * FROM BasicRoomEntity WHERE uid = :uid")
    abstract fun findByUid(uid: Long): BasicRoomEntity?

    @Query("SELECT * FROM BasicRoomEntity WHERE uid = :uid")
    abstract fun findByUidLive(uid: Long): LiveData<BasicRoomEntity>?

}