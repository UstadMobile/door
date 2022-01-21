package com.ustadmobile.door.util

import androidx.room.InvalidationTracker
import com.ustadmobile.door.ChangeListenerRequest

/**
 * This is a simple adapter class that adapts events from Room's InvalidationTracker.Observer to our own
 * ChangeListenerRequest
 */
internal class ChangeListenerRequestInvalidationObserver(
    private val changeListenerRequest: ChangeListenerRequest
) : InvalidationTracker.Observer(changeListenerRequest.tableNames.toTypedArray()) {
    override fun onInvalidated(tables: MutableSet<String>) {
        changeListenerRequest.onInvalidated.onTablesInvalidated(tables.toList())
    }

    override fun equals(other: Any?): Boolean {
        return if(other is ChangeListenerRequestInvalidationObserver)
            other.changeListenerRequest == this.changeListenerRequest
        else
            false
    }

    override fun hashCode(): Int {
        return changeListenerRequest.hashCode()
    }
}