package com.ustadmobile.door

/**
 * Callback class. This class will set the nodeId, and insert required rows into SqliteChangeSeqNums and
 * TableSyncStatus
 */
class SyncNodeIdCallback(val nodeId: Long): DoorDatabaseCallbackStatementList {

    private fun generateSetSyncNodeSql() : List<String>{
        return listOf("DELETE FROM SyncNode",
            """
            INSERT INTO SyncNode(nodeClientId)
                    VALUES ($nodeId) 
            """)
    }

    fun initSyncNodeSync(forceReset: Boolean): List<String> {
        if(forceReset)
            return generateSetSyncNodeSql()
        else
            return listOf()
    }

    override fun onCreate(db: DoorSqlDatabase) : List<String> {
        return generateSetSyncNodeSql()
    }

    override fun onOpen(db: DoorSqlDatabase) : List<String>{
        return listOf()
    }

}