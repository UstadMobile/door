package com.ustadmobile.door

import com.ustadmobile.door.ext.execSqlBatch

/**
 * Callback class. This class will set the nodeId, and insert required rows into SqliteChangeSeqNums and
 * TableSyncStatus
 */
class SyncNodeIdCallback(val nodeId: Long): DoorDatabaseCallbackSync {

    private fun setSyncNode(execSqlFn: (Array<String>) -> Unit) {
        execSqlFn(arrayOf("DELETE FROM SyncNode",
            """
            INSERT INTO SyncNode(nodeClientId)
                    VALUES ($nodeId) 
            """))
    }

    fun initSyncTables(forceReset: Boolean, execSqlFn: (Array<String>) -> Unit) {
        if(forceReset)
            setSyncNode(execSqlFn)

    }

    override fun onCreate(db: DoorSqlDatabase) {
        setSyncNode() { db.execSqlBatch(it) }
    }

    override fun onOpen(db: DoorSqlDatabase) {

    }

}