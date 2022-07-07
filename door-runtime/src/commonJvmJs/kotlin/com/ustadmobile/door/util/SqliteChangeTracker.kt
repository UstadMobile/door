package com.ustadmobile.door.util

import androidx.room.RoomDatabase
import com.ustadmobile.door.DoorDatabaseJdbc
import com.ustadmobile.door.ext.*
import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.door.jdbc.ext.mapRows
import com.ustadmobile.door.jdbc.ext.useResults

/**
 * The SQLite Change Tracker is based on using a temporary table and temporary triggers. Temporary tables and triggers
 * are isolated between JDBC connections.
 *
 * For this to work, setupTriggers(connection) must be called when a connection is first obtained, and then
 * findChangedTables(connection) must be called just before closing the connection.
 *
 * When not using a transaction, this is done by the DoorDatabaseJdbc.useConnection to detect changes and dispatch
 * them using the DoorInvalidationTracker
 *
 */
class SqliteChangeTracker(
    private val dbMetaData: DoorDatabaseMetadata<*>
) {

    //Use this on SQLite.JS
    private fun generateCreateTriggersSql(temporary: Boolean = true) : List<String>{
        val tempStr = if(temporary) "TEMP" else ""
        val createTableSql = if(temporary) CREATE_TEMP_TABLE_SQL else CREATE_TABLE_SQL
        return listOf(createTableSql) + dbMetaData.allTables.mapIndexed { tableId, tableName ->
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

    fun setupTriggersOnConnection(connection: Connection) {
        connection.createStatement().useStatement { stmt ->
            generateCreateTriggersSql().forEach { sql ->
                stmt.executeUpdate(sql)
            }
        }
    }

    suspend fun setupTriggersOnDbAsync(
        db: RoomDatabase,
        temporary: Boolean = true,
    ) {
        db.execSqlBatchAsync(*generateCreateTriggersSql(temporary).toTypedArray())
    }

    fun findChangedTablesOnConnection(connection: Connection): List<String> {
        val changedTables = connection.prepareStatement(FIND_CHANGED_TABLES_SQL).useStatement {stmt ->
            stmt.executeQuery().useResults { results ->
                results.mapRows {
                    dbMetaData.allTables[it.getInt(1)]
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