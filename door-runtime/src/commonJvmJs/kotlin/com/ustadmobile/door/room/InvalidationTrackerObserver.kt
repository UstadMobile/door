package com.ustadmobile.door.room

actual abstract class InvalidationTrackerObserver actual constructor(val tables: Array<String>) {
    actual abstract fun onInvalidated(tables: Set<String>)
}