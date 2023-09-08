package com.ustadmobile.door.http

import com.ustadmobile.door.room.RoomDatabase

data class DbAndDao<T: Any>(
    val db: RoomDatabase,
    val dao: T,
)