package com.ustadmobile.door

/**
 * Changes to syncable entities must be done via the repository. The repository manages setting
 * the last changed by fields and autogenerating the primary key (on SQLite). The database can be
 * used for read only (e.g. select queries) where one wants to get an immediate response without
 * the potential to check the server.
 *
 * The SyncableReadOnlyWrapper will be generated for any database that implements SyncableDoorDatabase.
 * It provides access to DAOs that will throw an exception if there is any attempt to use them to
 * modify an entity annotated with SyncableEntity.
 */
interface DoorDatabaseSyncableReadOnlyWrapper {

    val realDatabase: DoorDatabase

    companion object {

        const val SUFFIX = "_DbSyncableReadOnlyWrapper"

    }
}