package com.ustadmobile.door.room

actual abstract class InvalidationTrackerObserver actual constructor(val tables: Array<out String>) {
    actual abstract fun onInvalidated(tables: Set<String>)
}