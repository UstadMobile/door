package com.ustadmobile.door

import androidx.room.RoomDatabase
import com.ustadmobile.door.util.DoorJsImplClasses
import kotlin.reflect.KClass

data class DatabaseBuilderOptions<T : RoomDatabase>(
    var dbClass: KClass<T>,
    var dbImplClasses: DoorJsImplClasses<T>,
    var dbName: String = dbClass.simpleName!!,

    /**
     * Path to SQL.JS web worker
     */
    var webWorkerPath: String,

    /**
     * Delay time before saving the database to indexed database
     */
    val saveToIndexedDbDelayTime: Long = 200,

    var jdbcQueryTimeout: Int = 10
)
