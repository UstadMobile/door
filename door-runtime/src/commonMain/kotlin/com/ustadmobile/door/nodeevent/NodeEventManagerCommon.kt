package com.ustadmobile.door.nodeevent

import com.ustadmobile.door.ext.doorDatabaseMetadata
import com.ustadmobile.door.ext.withDoorTransactionAsync
import com.ustadmobile.door.log.DoorLogger
import com.ustadmobile.door.log.d
import com.ustadmobile.door.log.v
import com.ustadmobile.door.message.DoorMessage
import com.ustadmobile.door.message.DoorMessageCallback
import com.ustadmobile.door.replication.insertEntitiesFromMessage
import com.ustadmobile.door.room.RoomDatabase
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Common code for implementation of NodeEventManager.
 *
 */
@Suppress("PropertyName")
abstract class NodeEventManagerCommon<T : RoomDatabase>(
    protected val db: T,
    protected val messageCallback: DoorMessageCallback<T>,
    override val logger: DoorLogger,
    final override val dbName: String,
    protected val dispatcher: CoroutineDispatcher = Dispatchers.Default,
): NodeEventManager<T> {

    protected val logPrefix = "[NodeEventManager - $dbName]"

    protected val hasOutgoingReplicationTable = "OutgoingReplication" in db::class.doorDatabaseMetadata().allTables

    protected val _outgoingEvents = MutableSharedFlow<List<NodeEvent>>()

    override val outgoingEvents: Flow<List<NodeEvent>> = _outgoingEvents.asSharedFlow()

    private val _incomingMessages  = MutableSharedFlow<DoorMessage>()

    /**
     * The flow of incoming messages being received from other nodes. This is received as follows:
     *    - HTTP server: received via /receiveMessage endpoint
     *    - HTTP client: received via the server sent events (SSE) stream
     *    - Bluetooth: received via the Bluetooth server socket
     *
     * The flow is observed by:
     *    - IncomingReplicationHandler: runs the insert for the given entity (e.g. insert into receiveview)
     *    - InvalidationHandler (for later): an event could be emitted to indicate that something has been invalidated
     *      e.g. to trigger repository flow invalidation
     */
    override val incomingMessages: Flow<DoorMessage> = _incomingMessages.asSharedFlow()


    protected val closed = atomic(false)

    init {

    }

    protected fun assertNotClosed() {
        if(closed.value)
            throw IllegalStateException("NodeEventManager is closed!")
    }

    override suspend fun onIncomingMessageReceived(message: DoorMessage) {
        assertNotClosed()
        try {
            //this should check what is the strategy on the replicate entity
            logger.v { "$logPrefix receiveMessage with ${message.replications.size} replications: run inserts" }
            db.withDoorTransactionAsync {
                val messageToProcess = messageCallback.onIncomingMessageReceived(db, message)
                db.insertEntitiesFromMessage(messageToProcess)
                messageCallback.onIncomingMessageProcessed(db, messageToProcess)
            }
            logger.d { "$logPrefix receiveMessage with ${message.replications.size} replications: inserts done/transaction finished" }

            _incomingMessages.emit(message)
        }catch(e: Exception){
            throw e
        }
    }

    open fun close() {
        closed.value = true
    }

}