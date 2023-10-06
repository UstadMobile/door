package com.ustadmobile.door

import com.ustadmobile.door.log.DoorLogger
import com.ustadmobile.door.log.NapierDoorLogger
import com.ustadmobile.door.message.DefaultDoorMessageCallback
import com.ustadmobile.door.message.DoorMessageCallback
import com.ustadmobile.door.room.RoomDatabase
import com.ustadmobile.door.util.DoorJsImplClasses
import kotlin.reflect.KClass

data class DatabaseBuilderOptions<T : RoomDatabase>(
    var dbClass: KClass<T>,
    var dbImplClasses: DoorJsImplClasses<T>,
    val nodeId: Long,
    var dbUrl: String = "indexeddb:${dbClass.simpleName!!}",

    /**
     * Path to SQL.JS web worker
     */
    var webWorkerPath: String,

    /**
     * Delay time before saving the database to indexed database
     */
    val saveToIndexedDbDelayTime: Long = 200,

    val messageCallback: DoorMessageCallback<T> = DefaultDoorMessageCallback(),

    var jdbcQueryTimeout: Int = 10,

    /**
     * The database name that will be used in log messages
     */
    var dbName: String = dbUrl,

    /**
     * The logger to use
     */
    var logger: DoorLogger = NapierDoorLogger(),

    /**
     * If true, then log messages sent to/from the web worker. This is VERY verbose.
     */
    var logWorkerMessages: Boolean = false,
)
