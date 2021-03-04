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
     * Listen for incoming sync changes. This can be used to trigger logic that is required to
     * update clients (e.g. when a permission change happens granting a client access to an entity
     * it didn't have access to before).
     */
    //fun <T : Any> addSyncListener(entityClass: KClass<T>, listener: SyncListener<T>)

    /**
     * This is to be called from generated code on the SyncDao's HTTP Endpoint (e.g.
     * DbNameSyncDao_KtorRoute). It is called after entities are received from an incoming sync. It
     * will trigger any SyncListeners that were added using addSyncListener
     *
     * @param entityClass
     */
    //fun <T: Any> handleSyncEntitiesReceived(entityClass: KClass<T>, entities: List<T>)

    /**
     * Do not call this on the main thread: it might run a query
     */
    val clientId: Int
}