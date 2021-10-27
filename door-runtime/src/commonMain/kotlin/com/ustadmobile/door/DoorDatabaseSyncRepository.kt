package com.ustadmobile.door

import com.ustadmobile.door.daos.ISyncHelperEntitiesDao

/**
 * Interface implemented by the repository for any database that implements SyncableDoorDatabase.
 */
interface DoorDatabaseSyncRepository: DoorDatabaseRepository {

    /**
     * Syncs the given tables. This is implemented by generated code
     *
     * @param tablesToSync a list of the table ids to sync. If null, then sync all tables.
     */
    suspend fun sync(tablesToSync: List<Int>?) : List<SyncResult>

    /**
     * Interface that provides access to the functions in SyncHelperEntitiesDao
     *
     * @see ISyncHelperEntitiesDao
     */
    val syncHelperEntitiesDao: ISyncHelperEntitiesDao

    /**
     * Get the next primary key id for the given table. This uses DoorPrimaryKeyManager
     *
     * @param tableId the table id to get the next primary key for
     */
    fun nextId(tableId: Int): Long

    /**
     * Get the next primary key id for the given table. This uses DoorPrimaryKeyManager. Due to the
     * way primary keys are generated it is possible there might be a wait for the next available
     * primary key (see DoorPrimaryKeyManager). The suspended version of this function will use the
     * delay function instead of blocking to wait for the next primary key.
     *
     * @param tableId the table id to get the next primary key for
     */
    suspend fun nextIdAsync(tableId: Int): Long

    /**
     * If this is a client repository, then force the client to resync all tables.
     * This is done using ClientSyncManager.invalidateAllTables
     */
    suspend fun invalidateAllTables()

    /**
     * Do not call this on the main thread: it might run a query
     */
    val clientId: Long
}