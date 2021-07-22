package com.ustadmobile.door

import com.ustadmobile.door.ext.dbType
import com.ustadmobile.door.ext.execSqlBatch
import com.ustadmobile.door.util.systemTimeInMillis

/**
 * Callback class. This class will set the nodeId, and insert required rows into SqliteChangeSeqNums and
 * TableSyncStatus
 */
class DoorSyncableDatabaseCallback2(val nodeId: Int, val tableMap: Map<String, Int>, val primary: Boolean): DoorDatabaseCallback {

    private fun setSyncNode(dbType: Int, execSqlFn: (Array<String>) -> Unit) {
        val primaryStr = if(dbType == DoorDbType.SQLITE) {
            if(primary) "1" else "0"
        }else {
            primary.toString()
        }

        execSqlFn(arrayOf("DELETE FROM SyncNode",
            """
            INSERT INTO SyncNode(nodeClientId, master)
                    VALUES ($nodeId, $primaryStr) 
            """))
    }

    fun initSyncTables(dbType: Int, forceReset: Boolean, execSqlFn: (Array<String>) -> Unit) {
        val timeNow = systemTimeInMillis()

        if(dbType == DoorDbType.SQLITE) {
            val onConflictPrefix = if(forceReset) {
                " OR REPLACE  "
            }else {
                " OR IGNORE "
            }

            execSqlFn(tableMap.entries.flatMap { tableEntry ->
                listOf("""INSERT $onConflictPrefix INTO TableSyncStatus(tsTableId, tsLastChanged, tsLastSynced) 
                                VALUES(${tableEntry.value}, $timeNow, 0)""",
                    """INSERT $onConflictPrefix INTO SqliteChangeSeqNums(sCsnTableId, sCsnNextLocal, sCsnNextPrimary)
                          VALUES (${tableEntry.value}, 1, 1)""")
            }.toTypedArray())
        }else if(dbType == DoorDbType.POSTGRES) {
            execSqlFn(tableMap.entries.flatMap { tableEntry ->
                listOf("""
                    INSERT INTO TableSyncStatus(tsTableId, tsLastChanged, tsLastSynced)
                           VALUES(${tableEntry.value}, $timeNow, 0)
                           ON CONFLICT(tsTableId)
                              DO UPDATE
                                 SET tsLastChanged = excluded.tsLastChanged,
                                     tsLastSynced = excluded.tsLastSynced
                    """)
            }.toTypedArray())
        }else {
            throw IllegalStateException("Unsupported database type")
        }

        if(forceReset)
            setSyncNode(dbType, execSqlFn)

    }

    /**
     *
     */
    fun initSyncTables(db: DoorSqlDatabase, forceReset: Boolean = false) {
        initSyncTables(db.dbType(), forceReset) { sqlStatements ->
            db.execSqlBatch(sqlStatements)
        }
    }

    fun initSyncTables(repo: DoorDatabaseRepository, forceReset: Boolean) {
        initSyncTables(repo.db.dbType(), forceReset) { sqlStatements ->
            repo.db.execSqlBatch(*sqlStatements)
        }
    }

    override fun onCreate(db: DoorSqlDatabase) {
        setSyncNode(db.dbType()) { db.execSqlBatch(it) }
        initSyncTables(db, false)
    }

    override fun onOpen(db: DoorSqlDatabase) {
        //This is executed on open to ensure that the SqliteChangeSeqNum and TableSyncStatus is added for any tables
        // that might have been added via migration.
        initSyncTables(db, false)
    }

}