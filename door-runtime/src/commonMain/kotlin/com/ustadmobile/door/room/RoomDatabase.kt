package com.ustadmobile.door.room

expect abstract class RoomDatabase() {

    abstract fun clearAllTables()

    open val invalidationTracker: InvalidationTracker

}