package com.ustadmobile.door.room

import com.ustadmobile.door.ext.concurrentSafeListOf
import com.ustadmobile.door.ext.prepareStatementAsyncOrFallback
import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.door.jdbc.ext.*
import com.ustadmobile.door.util.InvalidationTrackerTransactionListener
import com.ustadmobile.door.util.jsDebug
import io.github.aakira.napier.Napier
import io.ktor.util.*

/**
 * Invalidation tracker for SQLite based databases (e.g. SQLite JDBC and IndexedDB on SQLite/JS). Works by setting
 * up triggers
 *
 */
internal open class SqliteInvalidationTracker(
    private val tableNames: Array<String>,
    private val setupTriggersBeforeConnection: Boolean = false,
): InvalidationTracker(), InvalidationTrackerTransactionListener {

    private val pendingChangesByConnection: MutableMap<Connection, MutableList<String>> = mutableMapOf()

    private val observers = concurrentSafeListOf<InvalidationTrackerObserver>()

    override fun beforeTransactionBlock(connection: Connection) {
        if(setupTriggersBeforeConnection) {
            connection.createStatement().useStatement { stmt ->
                generateCreateTriggersSql(tableNames).forEach { sql ->
                    stmt.executeUpdate(sql)
                }
            }
        }
    }

    override suspend fun beforeAsyncTransasctionBlock(connection: Connection) {
        if(setupTriggersBeforeConnection){
            setupTriggersAsync(connection)
        }
    }

    protected suspend fun setupTriggersAsync(connection: Connection, temporary: Boolean = false) {
        connection.createStatement().useStatementAsync { stmt ->
            val statementList = generateCreateTriggersSql(tableNames, temporary)
            Napier.d("setupTriggersAsync: got ${statementList.size} statements to run\n")
            statementList.forEach { sql ->
                Napier.d("setupTriggersAsync: run $sql\n")
                stmt.executeUpdateAsync(sql)
                Napier.d("setupTriggersAsync: ran $sql\n")
            }
            Napier.d("setupTriggersAsync: done running statements\n")
        }
        Napier.d("setupTriggersAsync: statement used\n")
    }

    override fun afterTransactionBlock(connection: Connection) {
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

        pendingChangesByConnection.getOrPut(connection) { mutableListOf() }.addAll(changedTables)
    }

    private suspend fun findChangedTables(connection: Connection) : List<String>{
        return connection.prepareStatementAsyncOrFallback(FIND_CHANGED_TABLES_SQL).useStatementAsync { stmt ->
            stmt.executeQueryAsyncKmp().useResults { results ->
                results.mapRows {
                    tableNames[it.getInt(1)]
                }.also {
                    println("changedtables: =$it\n")
                    jsDebug()
                }
            }
        }
    }

    override suspend fun afterAsyncTransactionBlock(connection: Connection) {
        //This is a strange syntax: using a more conventional val changeTables = ... fails to work on JS for absolutely
        // no apparent reason - and results in the list of tables being undefined.
        connection.prepareStatementAsyncOrFallback(FIND_CHANGED_TABLES_SQL).useStatementAsync { stmt ->
            stmt.executeQueryAsyncKmp().useResults { results ->
                results.mapRows {
                    tableNames[it.getInt(1)]
                }.also {
                    pendingChangesByConnection.getOrPut(connection) { mutableListOf() }.addAll(it)
                }
            }
        }

        connection.prepareStatementAsyncOrFallback(RESET_CHANGED_TABLES_SQL).useStatementAsync { stmt ->
            stmt.executeUpdateAsyncKmp()
        }
    }

    private fun fireChanges(changedTables: Set<String>) {
        val affectedObservers = observers.filter { observer ->
            observer.tables.any { changedTables.contains(it) }
        }

        affectedObservers.forEach {
            it.onInvalidated(changedTables)
        }
    }

    override fun afterTransactionCommitted(connection: Connection) {
        val tables = pendingChangesByConnection[connection]
        pendingChangesByConnection.remove(connection)
        if(!tables.isNullOrEmpty())
            fireChanges(tables.toSet())
    }

    override suspend fun afterTransactionCommittedAsync(connection: Connection) = afterTransactionCommitted(connection)

    override fun addObserver(observer: InvalidationTrackerObserver) {
        observers += observer
    }

    override fun removeObserver(observer: InvalidationTrackerObserver) {
        observers += observer
    }



    companion object {

        fun generateCreateTriggersSql(tableNames: Array<String>, temporary: Boolean = true) : List<String>{
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