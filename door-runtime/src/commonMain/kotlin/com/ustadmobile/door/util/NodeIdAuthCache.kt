package com.ustadmobile.door.util


import androidx.room.RoomDatabase
import com.ustadmobile.door.entities.DoorNode
import com.ustadmobile.door.ext.DoorTag
import com.ustadmobile.door.ext.concurrentSafeListOf
import com.ustadmobile.door.ext.concurrentSafeMapOf
import com.ustadmobile.door.replication.getDoorNodeAuth
import com.ustadmobile.door.replication.insertNewDoorNode
import io.github.aakira.napier.Napier
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * NodeIdAuthCache will cache nodeId and auth strings to reduce the number of database queries required. Used when
 * acting as a server for other (remote) nodes to receive replication updates from the local node.
 */
class NodeIdAuthCache(
    private val db: RoomDatabase
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
                Napier.d("NodeIdAndAuthCache: New Node connected: $nodeId ", tag = DoorTag.LOG_TAG)
                //new node just arrived
                db.insertNewDoorNode(DoorNode().also {
                    it.auth = auth
                    it.nodeId = nodeId
                    it.rel = DoorNode.SERVER_FOR
                })
                cachedAuth[nodeId] = auth
                Napier.d("NodeIdAndAuthCache: Fire new node event to ${newNodeListeners.size} listeners")
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