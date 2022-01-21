package com.ustadmobile.door.util

import com.ustadmobile.door.ChangeListenerRequest
import com.ustadmobile.door.DoorDatabase
import com.ustadmobile.door.DoorDatabaseJdbc
import com.ustadmobile.door.ext.DoorTag
import com.ustadmobile.door.ext.concurrentSafeListOf
import com.ustadmobile.door.ext.rootDatabase
import io.github.aakira.napier.Napier
import kotlin.jvm.Synchronized

/**
 * The TransactionInvalidationTracker is used by Jdbc database implementations to track which tables have changed and
 * fire events accordingly.
 *
 * The root database instance (where transaction depth = 0) will maintain the list of event listeners and fire any changes
 * received immediately.
 *
 * The instance for transaction db wrappers will maintain a list of what is changed through the transaction, and will
 * fire changes via the root database instance TransactionInvalidationListener after onCommit is called.
 *
 */
class DoorInvalidationTracker(
    private val jdbcDatabase: DoorDatabaseJdbc
) {

    private val tablesChanged = concurrentSafeListOf<String>()

    private val listeners = concurrentSafeListOf<ChangeListenerRequest>()

    internal fun onTablesInvalidated(tableNames: Set<String>) {
        tablesChanged += tableNames
        if(!jdbcDatabase.isInTransaction) {
            fireChanges()
        }
    }

    fun onCommit() {
        if(jdbcDatabase.isInTransaction && tablesChanged.isNotEmpty()) {
            val rootJdbcDb = (jdbcDatabase as DoorDatabase).rootDatabase as DoorDatabaseJdbc
            rootJdbcDb.invalidationTracker.onTablesInvalidated(tablesChanged.toSet())
        }
    }

    @Synchronized
    private fun getChangeListAndClear() : List<String>{
        val changeList = tablesChanged.toSet().toList()
        tablesChanged.clear()
        return changeList
    }

    private fun fireChanges() {
        val listToFire = getChangeListAndClear()

        val affectedChangeListeners = listeners.filter { changeListener ->
            changeListener.tableNames.any { listToFire.contains(it) }
        }
        Napier.d("Invalidation Tracker for [${this.jdbcDatabase}] notifying ${affectedChangeListeners.size} listeners of changes to " +
                listToFire.joinToString(), tag = DoorTag.LOG_TAG)
        affectedChangeListeners.forEach {
            it.onInvalidated.onTablesInvalidated(listToFire)
        }
    }

    internal fun addInvalidationListener(changeListenerRequest: ChangeListenerRequest) {
        listeners += changeListenerRequest
    }

    internal fun removeInvalidationListener(changeListenerRequest: ChangeListenerRequest) {
        listeners -= changeListenerRequest
    }


}