package com.ustadmobile.door.room

expect abstract class RoomDatabase() {

    abstract fun clearAllTables()

    open fun getInvalidationTracker(): InvalidationTracker

    open fun close()

}