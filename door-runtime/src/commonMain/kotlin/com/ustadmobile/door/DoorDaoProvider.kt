package com.ustadmobile.door

import androidx.room.RoomDatabase

class DoorDaoProvider<T: RoomDatabase, D>(val providerFn: (T) -> D) {

    fun getDao(db: T) = providerFn(db)

}