package androidx.room

import com.ustadmobile.door.ext.concurrentSafeListOf
import com.ustadmobile.door.ext.useStatement
import com.ustadmobile.door.ext.useStatementAsync
import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.door.jdbc.ext.executeQueryAsyncKmp
import com.ustadmobile.door.jdbc.ext.executeUpdateAsync
import com.ustadmobile.door.jdbc.ext.mapRows
import com.ustadmobile.door.jdbc.ext.useResults
import kotlin.jvm.Volatile

open class InvalidationTracker(
    private val db: RoomDatabase,
    vararg tables: String,
) {

    private val tableNames: List<String> = tables.toList()

    private val observers = concurrentSafeListOf<Observer>()

    abstract class Observer(val tables: Array<String>) {

        abstract fun onInvalidated(tables: Set<String>)

    }

    fun addObserver(observer: Observer) {
        observers += observer
    }

    fun removeObserver(observer: Observer) {
        observers -= observer
    }

    fun onTablesInvalidated(tableNames: Set<String>) {
        fireChanges(tableNames)
    }

    private fun fireChanges(listToFire: Set<String>) {
        val affectedObservers = observers.filter { observer ->
            observer.tables.any { listToFire.contains(it) }
        }

        affectedObservers.forEach {
            it.onInvalidated(listToFire)
        }
    }

    private fun generateCreateTriggersSql(temporary: Boolean = true) : List<String>{
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

    fun setupSqliteTriggers(connection: Connection) {
        connection.createStatement().useStatement { stmt ->
            generateCreateTriggersSql().forEach {  sql ->
                stmt.executeUpdate(sql)
            }
        }
    }

    suspend fun setupSqliteTriggersAsync(connection: Connection) {
        connection.createStatement().useStatementAsync { stmt ->
            generateCreateTriggersSql().forEach { sql ->
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
            stmt.executeUpdate()
        }

        return changedTables
    }


    companion object {
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