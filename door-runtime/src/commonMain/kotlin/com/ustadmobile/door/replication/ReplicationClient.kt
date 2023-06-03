package com.ustadmobile.door.replication

import com.ustadmobile.door.RepositoryConfig
import com.ustadmobile.door.room.RoomDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ReplicationClient(
    private val repositoryConfig: RepositoryConfig,
    private val doorWrappedDb:  RoomDatabase,
    scope: CoroutineScope? = null,
) {

    private val replicationScope = scope ?: CoroutineScope(Dispatchers.Default + Job())

    init {
        replicationScope.launch {
            //get the node id on the other side, then listen for replications that need to go to the server.
        }
    }
}