package com.ustadmobile.door.util

import com.ustadmobile.door.room.RoomDatabase
import com.ustadmobile.door.ext.DoorDatabaseMetadata
import kotlin.reflect.KClass

/**
 * This is used on Javascript because we can't use Class.forName . It has KClass member variables for all generated
 * implementations. An object type will be generated for each database class. That object is then passed as a parameter
 * to the DatabaseBuilder.
 */
abstract class DoorJsImplClasses<T: RoomDatabase> () {
    //KClass for the original database itself (not the implementation)
    abstract val dbKClass: KClass<T>

    abstract val dbImplKClass: KClass<*>

    abstract val replicateWrapperImplClass: KClass<*>?

    abstract val repositoryImplClass: KClass<*>?

    abstract val metadata: DoorDatabaseMetadata<T>
}

