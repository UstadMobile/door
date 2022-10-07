package com.ustadmobile.door.room

import com.ustadmobile.door.ext.concurrentSafeListOf

actual open class InvalidationTracker() {

    protected val observers = concurrentSafeListOf<InvalidationTrackerObserver>()

    actual open fun addObserver(observer: InvalidationTrackerObserver) {
        observers += observer
    }

    actual open fun removeObserver(observer: InvalidationTrackerObserver) {
        observers -= observer
    }

    protected fun fireChanges(changedTables: Set<String>) {
        val affectedObservers = observers.filter { observer ->
            observer.tables.any { changedTables.contains(it) }
        }

        affectedObservers.forEach {
            it.onInvalidated(changedTables)
        }
    }


}