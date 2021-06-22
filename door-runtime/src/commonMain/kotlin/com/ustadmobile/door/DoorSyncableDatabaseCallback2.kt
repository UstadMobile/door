package com.ustadmobile.door

import com.ustadmobile.door.ext.execSql
import com.ustadmobile.door.util.systemTimeInMillis

/**
 * Callback class. This class will set the nodeId, and insert required rows into SqliteChangeSeqNums and
 * TableSyncStatus
 */
class DoorSyncableDatabaseCallback2(val nodeId: Int, val tableMap: Map<String, Int>, val primary: Boolean): DoorDatabaseCallback {

    fun setSyncNode(execSqlFn: (String) -> Unit) {
        execSqlFn("""
            INSERT INTO SyncNode(nodeClientId, master)
                    VALUES ($nodeId, ${if(primary) 1 else 0}) 
            """)
    }

    fun initSyncTables(forceReset: Boolean, execSqlFn: (String) -> Unit) {
        val onConflictPrefix = if(forceReset) {
            " OR REPLACE  "
        }else {
            " OR IGNORE "
        }

        tableMap.entries.forEach { tableEntry ->
            execSqlFn("""INSERT $onConflictPrefix INTO TableSyncStatus(tsTableId, tsLastChanged, tsLastSynced) 
                                VALUES(${tableEntry.value}, ${systemTimeInMillis()}, 0)""")

            execSqlFn("""
                INSERT $onConflictPrefix INTO SqliteChangeSeqNums(sCsnTableId, sCsnNextLocal, sCsnNextPrimary)
                       VALUES (${tableEntry.value}, 1, 1)
                       
            """)
        }

        if(forceReset)
            setSyncNode(execSqlFn)

    }

    /**
     *
     */
    fun initSyncTables(db: DoorSqlDatabase, forceReset: Boolean = false) {
        initSyncTables(forceReset) { sql ->
            db.execSQL(sql)
        }
    }

    fun initSyncTables(repo: DoorDatabaseRepository, forceReset: Boolean) {
        initSyncTables(forceReset) { sql ->
            repo.db.execSql(sql)
        }
    }

    override fun onCreate(db: DoorSqlDatabase) {
        setSyncNode { db.execSQL(it) }
        initSyncTables(db, false)
    }

    override fun onOpen(db: DoorSqlDatabase) {
        //This is executed on open to ensure that the SqliteChangeSeqNum and TableSyncStatus is added for any tables
        // that might have been added via migration.
        initSyncTables(db, false)
    }

}