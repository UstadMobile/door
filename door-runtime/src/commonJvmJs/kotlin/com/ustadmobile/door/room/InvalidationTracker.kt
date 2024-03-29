package com.ustadmobile.door.room

import com.ustadmobile.door.ext.DoorTag
import com.ustadmobile.door.ext.concurrentSafeListOf
import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.door.jdbc.ext.*
import com.ustadmobile.door.util.IWeakRef
import com.ustadmobile.door.util.weakRefOf
import io.github.aakira.napier.Napier

actual open class InvalidationTracker(
    vararg tables: String,
) {

    private val tableNames: List<String> = tables.toList()

    private val observers = concurrentSafeListOf<InvalidationTrackerObserver>()

    private val weakObservers = concurrentSafeListOf<IWeakRef<InvalidationTrackerObserver>>()

    actual open fun addObserver(observer: InvalidationTrackerObserver) {
        observers += observer
    }

    actual open fun removeObserver(observer: InvalidationTrackerObserver) {
        observers -= observer
    }

    fun addWeakObserver(observer: InvalidationTrackerObserver) {
        weakObservers += weakRefOf(observer)
    }

    fun onTablesInvalidated(tableNames: Set<String>) {
        fireChanges(tableNames)
    }

    private fun fireChanges(listToFire: Set<String>) {
        Napier.v(tag = DoorTag.LOG_TAG) { "" +
            "InvalidationTracker: tables invalidated: ${listToFire.joinToString()}"
        }
        val affectedObservers = observers.filter { observer ->
            observer.tables.any { listToFire.contains(it) }
        }

        affectedObservers.forEach {
            it.onInvalidated(listToFire)
        }

        val affectedWeakObservers = weakObservers.mapNotNull { observerRef ->
            val observer = observerRef.get()
            if(observer != null && observer.tables.any { listToFire.contains(it) }) {
                observer
            }else {
                null
            }
        }

        affectedWeakObservers.forEach { it.onInvalidated(listToFire) }
        weakObservers.removeAll { it.get() == null }
    }

    fun setupSqliteTriggers(connection: Connection) {
        connection.createStatement().useStatement { stmt ->
            generateCreateTriggersSql(tableNames).forEach {  sql ->
                stmt.executeUpdate(sql)
            }
        }
    }

    suspend fun setupSqliteTriggersAsync(connection: Connection) {
        connection.createStatement().useStatementAsync { stmt ->
            generateCreateTriggersSql(tableNames).forEach { sql ->
                stmt.executeUpdateAsync(sql)
            }
        }
    }

    fun findChangedTablesOnConnection(connection: Connection): List<String> {
        val changedTables = connection.prepareStatement(FIND_CHANGED_TABLES_SQL).useStatement {stmt ->
            stmt.executeQuery().useResults { results ->
                results.mapRows {
                    tableNames[it.getInt(1)]
                }
            }
        }

        connection.prepareStatement(RESET_CHANGED_TABLES_SQL).useStatement { stmt ->
            stmt.executeUpdate()
        }

        return changedTables
    }

    suspend fun findChangedTablesOnConnectionAsync(connection: Connection): List<String> {
        val changedTables = connection.prepareStatement(FIND_CHANGED_TABLES_SQL).useStatementAsync {stmt ->
            stmt.executeQueryAsyncKmp().useResults { results ->
                results.mapRows {
                    tableNames[it.getInt(1)]
                }
            }
        }

        connection.prepareStatement(RESET_CHANGED_TABLES_SQL).useStatement { stmt ->
            stmt.executeUpdateAsyncKmp()
        }

        return changedTables
    }


    companion object {

        fun generateCreateTriggersSql(tableNames: List<String>, temporary: Boolean = true) : List<String>{
            //TODO: this could be a problem when number of tables is reduced...
            val tempStr = if(temporary) "TEMP" else ""
            val createTableSql = if(temporary) CREATE_TEMP_TABLE_SQL else CREATE_TABLE_SQL
            return listOf(createTableSql) + tableNames.mapIndexed { tableId, tableName ->
                listOf("INSERT OR IGNORE INTO $UPDATE_TABLE_NAME ($TABLE_ID_COLNAME, $TABLE_INVALIDATED_COLNAME) " +
                        "VALUES ($tableId, 0)") +
                        listOf("UPDATE", "INSERT", "DELETE").map { evtName ->
                            """CREATE $tempStr TRIGGER IF NOT EXISTS door_mod_trigger_${tableName}_${evtName} 
                   AFTER $evtName
                   ON $tableName 
                   BEGIN 
                   UPDATE $UPDATE_TABLE_NAME
                      SET $TABLE_INVALIDATED_COLNAME = 1 
                    WHERE $TABLE_ID_COLNAME = $tableId
                      AND $TABLE_INVALIDATED_COLNAME = 0;
                    END 
                    """
                        }
            }.flatten()
        }

        const val UPDATE_TABLE_NAME = "door_update_mods"

        const val TABLE_ID_COLNAME = "tableId"

        const val TABLE_INVALIDATED_COLNAME = "invalidated"

        const val CREATE_TEMP_TABLE_SQL = "CREATE TEMP TABLE IF NOT EXISTS $UPDATE_TABLE_NAME " +
                "($TABLE_ID_COLNAME INTEGER PRIMARY KEY, $TABLE_INVALIDATED_COLNAME INTEGER NOT NULL DEFAULT 0)"

        const val CREATE_TABLE_SQL = "CREATE TABLE IF NOT EXISTS $UPDATE_TABLE_NAME " +
                "($TABLE_ID_COLNAME INTEGER PRIMARY KEY, $TABLE_INVALIDATED_COLNAME INTEGER NOT NULL DEFAULT 0)"

        const val FIND_CHANGED_TABLES_SQL = "SELECT tableId FROM $UPDATE_TABLE_NAME WHERE invalidated = 1"

        const val RESET_CHANGED_TABLES_SQL = """
            UPDATE $UPDATE_TABLE_NAME 
               SET $TABLE_INVALIDATED_COLNAME = 0 
             WHERE $TABLE_INVALIDATED_COLNAME = 1
        """

    }

}