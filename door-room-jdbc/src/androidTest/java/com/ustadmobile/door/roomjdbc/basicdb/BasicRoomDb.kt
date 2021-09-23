package com.ustadmobile.door.roomjdbc.basicdb

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [BasicRoomEntity::class], version = 1)
abstract class BasicRoomDb : RoomDatabase(){

    abstract val basicDao: BasicRoomDao

}