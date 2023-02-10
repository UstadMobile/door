package com.ustadmobile.door.util

import androidx.room.InvalidationTracker
import androidx.room.RoomDatabase

/**
 * Repository classes and the wrapper class are children of the database class, which is a child of RoomDatabase.
 * Room's RoomDatabase constructor will call createInvalidationTracker() before the child class itself has been
 * initialized. This InvalidationTracker instance would never really be used (only the InvalidationTracker of the
 * database itself is used).
 *
 * This function creates a dummy InvalidationTracker to avoid any exceptions on initialization. Reflection must be used
 * to work around the RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
 */
fun RoomDatabase.makeDummyInvalidationHandler(): InvalidationTracker  {
    return InvalidationTracker::class.java.getConstructor(
        RoomDatabase::class.java, Map::class.java, Map::class.java, Array<String>::class.java
    ).newInstance(
        this, emptyMap<String, String>(), emptyMap<String, Set<String>>(), emptyArray<String>()
    )
}
