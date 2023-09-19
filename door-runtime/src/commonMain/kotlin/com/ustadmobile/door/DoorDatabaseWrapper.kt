package com.ustadmobile.door

import com.ustadmobile.door.nodeevent.NodeEventManagerCommon
import com.ustadmobile.door.room.RoomDatabase
import kotlin.reflect.KClass

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

    /**
     * This allows the lookup of a DAO according to the class. This is used by generated code, in particular generated
     * http endpoints where auth / additional replication data functions may be located on another DAO. This avoids the
     * need to copy/paste functions onto each DAO.
     */
    fun <T: Any> getDaoByClass(daoClass: KClass<T>): T

    companion object {

        /**
         * This is the class name suffix that will be used by generated ReadOnlyWrappers
         */
        const val SUFFIX = "_DoorWrapper"

    }
}