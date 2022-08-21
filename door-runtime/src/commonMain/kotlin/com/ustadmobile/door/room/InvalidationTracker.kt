package com.ustadmobile.door.room

expect open class InvalidationTracker {

    abstract class Observer(tables: Array<String>) {

        abstract fun onInvalidated(tables: Set<String>)

    }

    open fun addObserver(observer: InvalidationTracker.Observer)

    open fun removeObserver(observer: InvalidationTracker.Observer)
}