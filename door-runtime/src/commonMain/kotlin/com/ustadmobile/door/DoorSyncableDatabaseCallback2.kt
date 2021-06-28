package com.ustadmobile.door

import com.ustadmobile.door.ext.execSqlBatch
import com.ustadmobile.door.util.systemTimeInMillis

/**
 * Callback class. This class will set the nodeId, and insert required rows into SqliteChangeSeqNums and
 * TableSyncStatus
 */
class DoorSyncableDatabaseCallback2(val nodeId: Int, val tableMap: Map<String, Int>, val primary: Boolean): DoorDatabaseCallback {

    fun setSyncNode(execSqlFn: (Array<String>) -> Unit) {
        execSqlFn(arrayOf("DELETE FROM SyncNode",
            """
            INSERT INTO SyncNode(nodeClientId, master)
                    VALUES ($nodeId, ${if(primary) 1 else 0}) 
            """))
    }

    fun initSyncTables(forceReset: Boolean, execSqlFn: (Array<String>) -> Unit) {
        val onConflictPrefix = if(forceReset) {
            " OR REPLACE  "
        }else {
            " OR IGNORE "
        }

        execSqlFn(tableMap.entries.flatMap { tableEntry ->
            listOf("""INSERT $onConflictPrefix INTO TableSyncStatus(tsTableId, tsLastChanged, tsLastSynced) 
                                VALUES(${tableEntry.value}, ${systemTimeInMillis()}, 0)""",
                """INSERT $onConflictPrefix INTO SqliteChangeSeqNums(sCsnTableId, sCsnNextLocal, sCsnNextPrimary)
                          VALUES (${tableEntry.value}, 1, 1)""")
        }.toTypedArray())


        if(forceReset)
            setSyncNode(execSqlFn)

    }

    /**
     *
     */
    fun initSyncTables(db: DoorSqlDatabase, forceReset: Boolean = false) {
        initSyncTables(forceReset) { sqlStatements ->
            db.execSqlBatch(sqlStatements)
        }
    }

    fun initSyncTables(repo: DoorDatabaseRepository, forceReset: Boolean) {
        initSyncTables(forceReset) { sqlStatements ->
            repo.db.execSqlBatch(*sqlStatements)
        }
    }

    override fun onCreate(db: DoorSqlDatabase) {
        setSyncNode { db.execSqlBatch(it) }
        initSyncTables(db, false)
    }

    override fun onOpen(db: DoorSqlDatabase) {
        //This is executed on open to ensure that the SqliteChangeSeqNum and TableSyncStatus is added for any tables
        // that might have been added via migration.
        initSyncTables(db, false)
    }

}