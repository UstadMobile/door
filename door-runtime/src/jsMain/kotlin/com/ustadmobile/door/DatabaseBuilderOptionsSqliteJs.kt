package com.ustadmobile.door

import com.ustadmobile.door.room.RoomDatabase
import com.ustadmobile.door.util.DoorJsImplClasses
import kotlin.reflect.KClass

class DatabaseBuilderOptionsSqliteJs<T: RoomDatabase>(
    dbClass: KClass<T>,
    dbImplClasses: DoorJsImplClasses<T>,
    dbUrl: String,
    jdbcQueryTimeout: Int = 10,
    /**
     * Path to SQL.JS web worker
     */
    var webWorkerPath: String,

    /**
     * Delay time before saving the database to indexed database
     */
    val saveToIndexedDbDelayTime: Long = 200,
): DatabaseBuilderOptions<T>(
    dbClass, dbImplClasses, dbUrl, jdbcQueryTimeout
) {
}