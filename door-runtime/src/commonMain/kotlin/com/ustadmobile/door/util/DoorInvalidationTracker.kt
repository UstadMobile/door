package com.ustadmobile.door.util

import com.ustadmobile.door.ChangeListenerRequest
import com.ustadmobile.door.ext.DoorTag
import com.ustadmobile.door.ext.concurrentSafeListOf
import io.github.aakira.napier.Napier
import kotlin.jvm.Volatile

/**
 * The DoorInvalidationTracker is used by Jdbc database implementations. onTablesInvalidated is called by a
 * change tracker (e.g. SQLiteChangeTracker) when changes are committed.
 */
class DoorInvalidationTracker(
    private val logName: String
) {

    private val listeners = concurrentSafeListOf<ChangeListenerRequest>()

    /**
     * Change tracking is deliberately left inactive during the migration and creation process
     */
    @Volatile
    var active: Boolean = false
        internal set

    fun onTablesInvalidated(tableNames: Set<String>) {
        fireChanges(tableNames)

    }

    private fun fireChanges(listToFire: Set<String>) {
        val affectedChangeListeners = listeners.filter { changeListener ->
            changeListener.tableNames.any { listToFire.contains(it) }
        }
        Napier.d("Invalidation Tracker for [$logName] notifying ${affectedChangeListeners.size} listeners of changes to " +
                listToFire.joinToString(), tag = DoorTag.LOG_TAG)
        affectedChangeListeners.forEach {
            it.onInvalidated.onTablesInvalidated(listToFire.toList())
        }
    }

    internal fun addInvalidationListener(changeListenerRequest: ChangeListenerRequest) {
        listeners += changeListenerRequest
    }

    internal fun removeInvalidationListener(changeListenerRequest: ChangeListenerRequest) {
        listeners -= changeListenerRequest
    }


}