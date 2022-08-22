package com.ustadmobile.door.room

expect open class InvalidationTracker {

    open fun addObserver(observer: InvalidationTrackerObserver)

    open fun removeObserver(observer: InvalidationTrackerObserver)
}