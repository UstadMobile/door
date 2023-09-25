package com.ustadmobile.door

import com.ustadmobile.door.replication.DoorRepositoryReplicationClient
import com.ustadmobile.door.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * Common interface that is implemented by any repository. Can be used to get info including
 * the active endpoint, auth, database path and the http client.
 */
interface DoorDatabaseRepository {

    val config: RepositoryConfig

    /**
     * This provides access to the underlying database for this repository. It must be wrapped with
     * The SyncableReadOnlyWrapper if this is a syncable database.
     */
    val db: RoomDatabase

    val dbName: String

    var connectivityStatus: Int

    val clientState: Flow<DoorRepositoryReplicationClient.ClientState>

    fun remoteNodeIdOrNull(): Long?

    fun remoteNodeIdOrFake(): Long

    fun close()

}