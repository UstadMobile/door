package com.ustadmobile.door

import com.ustadmobile.door.nodeevent.NodeEventManagerCommon
import com.ustadmobile.door.room.RoomDatabase

/**
 * The DoorDatabaseWrapper takes care of door-specific behaviors when data is modified:
 *
 *   Setting @LastChangedTime fields on Insert and Update functions
 *   Using doorPrimaryKeyManager to set the primarykey on replicate entities on Insert functions
 *
 * DoorDatabaseWrapper also hosts the NodeEventManager
 */
interface DoorDatabaseWrapper<T: RoomDatabase> {

    /**
     * The underlying database: on JVM this is the JdbcImpl class, on Android this is the Room instance.
     */
    val realDatabase: RoomDatabase

    val dbName: String

    val nodeEventManager: NodeEventManagerCommon<T>

    val nodeId: Long

    companion object {

        /**
         * This is the class name suffix that will be used by generated ReadOnlyWrappers
         */
        const val SUFFIX = "_DoorWrapper"

    }
}