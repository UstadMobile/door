package com.ustadmobile.door.room

expect abstract class InvalidationTrackerObserver(tables: Array<String>) {
    abstract fun onInvalidated(tables: Set<String>)
}