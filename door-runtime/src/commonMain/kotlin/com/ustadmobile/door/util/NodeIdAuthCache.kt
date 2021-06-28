package com.ustadmobile.door.util

import com.ustadmobile.door.DoorDatabaseSyncRepository
import com.ustadmobile.door.entities.DoorNode
import com.ustadmobile.door.ext.concurrentSafeMapOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * NodeIdAuthCache will cache nodeId and auth strings to reduce the number of database queries required.
 */
class NodeIdAuthCache(private val syncRepo: DoorDatabaseSyncRepository) {

    private val cachedAuth: MutableMap<Int, String> = concurrentSafeMapOf()

    private val mutex = Mutex()

    suspend fun verify(nodeId: Int, auth: String) : Boolean {
        val cachedAuthResult = cachedAuth[nodeId]
        if(cachedAuthResult != null)
            return cachedAuthResult == auth

        return mutex.withLock {
            val dbAuthResult = syncRepo.syncHelperEntitiesDao.getDoorNodeAuth(nodeId)
            if(dbAuthResult != null) {
                cachedAuth[nodeId] = dbAuthResult
                dbAuthResult == auth
            }else {
                //new node just arrived
                syncRepo.syncHelperEntitiesDao.addDoorNode(DoorNode().also {
                    it.auth = auth
                    it.nodeId = nodeId
                })
                cachedAuth[nodeId] = auth
                true
            }
        }
    }

}