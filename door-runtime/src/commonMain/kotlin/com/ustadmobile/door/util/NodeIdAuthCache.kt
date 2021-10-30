package com.ustadmobile.door.util

import com.ustadmobile.door.DoorDatabase
import com.ustadmobile.door.entities.DoorNode
import com.ustadmobile.door.ext.concurrentSafeListOf
import com.ustadmobile.door.ext.concurrentSafeMapOf
import com.ustadmobile.door.replication.getDoorNodeAuth
import com.ustadmobile.door.replication.insertNewDoorNode
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * NodeIdAuthCache will cache nodeId and auth strings to reduce the number of database queries required.
 */
class NodeIdAuthCache(
    private val db: DoorDatabase
) {

    fun interface OnNewDoorNode {
        fun onNewDoorNode(newNodeId: Long, auth: String)
    }

    private val newNodeListeners = concurrentSafeListOf<OnNewDoorNode>()


    private val cachedAuth: MutableMap<Long, String> = concurrentSafeMapOf()

    private val mutex = Mutex()

    suspend fun verify(nodeId: Long, auth: String) : Boolean {
        val cachedAuthResult = cachedAuth[nodeId]
        if(cachedAuthResult != null)
            return cachedAuthResult == auth

        return mutex.withLock {
            val dbAuthResult = db.getDoorNodeAuth(nodeId)
            if(dbAuthResult != null) {
                cachedAuth[nodeId] = dbAuthResult
                dbAuthResult == auth
            }else {
                //new node just arrived
                db.insertNewDoorNode(DoorNode().also {
                    it.auth = auth
                    it.nodeId = nodeId
                })
                cachedAuth[nodeId] = auth
                newNodeListeners.forEach {
                    it.onNewDoorNode(nodeId, auth)
                }
                true
            }
        }
    }

    fun addNewNodeListener(newNodeListener: OnNewDoorNode) {
        newNodeListeners += newNodeListener
    }

    @Suppress("unused")
    fun removeNewNodeListener(newNodeListener: OnNewDoorNode) {
        newNodeListeners -= newNodeListener
    }

}