package com.ustadmobile.door.util

import com.ustadmobile.door.DoorDatabase
import com.ustadmobile.door.DoorDatabaseJdbc
import com.ustadmobile.door.ext.doorDatabaseMetadata
import com.ustadmobile.door.ext.mapRows
import com.ustadmobile.door.ext.useResults
import com.ustadmobile.door.ext.useStatement
import com.ustadmobile.door.jdbc.Connection

class SqliteChangeTracker(
    jdbcDatabase: DoorDatabaseJdbc
) {

    private val dbMetaData = (jdbcDatabase as DoorDatabase)::class.doorDatabaseMetadata()

    //Use this on SQLite.JS
    private fun generateCreateTriggersSql() : List<String>{
        return listOf(CREATE_TEMP_TABLE_SQL) + dbMetaData.allTables.mapIndexed { tableId, tableName ->
            listOf("INSERT OR IGNORE INTO $UPDATE_TABLE_NAME ($TABLE_ID_COLNAME, $TABLE_INVALIDATED_COLNAME) " +
                    "VALUES ($tableId, 0)") +
                    listOf("UPDATE", "INSERT", "DELETE").map { evtName ->
                        """CREATE TEMP TRIGGER IF NOT EXISTS door_mod_trigger_${tableName}_${evtName} 
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

    fun setupTriggers(connection: Connection) {
        connection.createStatement().useStatement { stmt ->
            generateCreateTriggersSql().forEach { sql ->
                stmt.executeUpdate(sql)
            }
        }
    }

    fun findChangedTables(connection: Connection): List<String> {
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

        const val FIND_CHANGED_TABLES_SQL = "SELECT tableId FROM $UPDATE_TABLE_NAME WHERE invalidated = 1"

        const val RESET_CHANGED_TABLES_SQL = """
            UPDATE $UPDATE_TABLE_NAME 
               SET $TABLE_INVALIDATED_COLNAME = 0 
             WHERE $TABLE_INVALIDATED_COLNAME = 1
        """

    }



}