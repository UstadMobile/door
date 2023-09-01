package com.ustadmobile.door.nodeevent

import com.ustadmobile.door.entities.OutgoingReplication
import com.ustadmobile.door.ext.doorDatabaseMetadata
import com.ustadmobile.door.replication.insertIntoRemoteReceiveView
import com.ustadmobile.door.room.RoomDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * A NodeEvent is an invalidation or replication that happens on one node, that may need to be sent to another node.
 * The NodeEvent itself contains only the destination node, table id, and primary key(s). Node events can be replications
 * or invalidations (only replications will currently be supported).
 *
 * The NodeEventManager listens for events (e.g. replications to send from any insert into the OutgoingReplication table)
 * and emits them on the outgoing events flow. The outgoing events flow is observed by any/all components that deliver
 * messages e.g. server sent event http endpoints, http repository client, and bluetooth client. If the event needs to be
 * transmitted (e.g. there is a client actively connected to the http server sent events endpoint, within bluetooth range,
 * etc), the component that observes the flow will convert the event into a NodeEventMessage. The NodeEventsMessage can
 * send multiple events and will contain the full information required by the other node (eg. the JSON data of the
 * entities being replicated).
 *
 * Notes: RepositoryClient can use server sent events client on http, on bluetooth it can send a heartbeat so that any
 * nearby proxy device knows it is still alive.
 *
 * On Android: this class will simply listen for invalidations of OutgoingReplication table, then run the query. Creation
 * of the trigger and temp table will be done by onOpen callback
 *
 * @param db - the unwrapped actual database (e.g. Room implementation on Android, JDBC implementation on JVM)
 *
 */
@Suppress("PropertyName")
abstract class NodeEventManagerCommon(
    protected val db: RoomDatabase,
    protected val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    protected val hasOutgoingReplicationTable = OutgoingReplication::class.simpleName?.let {
        it in db::class.doorDatabaseMetadata().allTables
    } ?: false

    protected val _outgoingEvents = MutableSharedFlow<List<NodeEvent>>()

    /**
     * The flow of outgoing events that may need to be delivered to other nodes, batched into lists (e.g. one list is
     * generated per database transaction).
     *
     * This flow will be observed by:
     *   - HTTP ServerSentEvents (SSE) endpoint (used by http server) - listen for events for the client listening, emit an
     *     event
     *   - DoorHttpClient - listen for events for upstream server. When event is received, make an HTTP put request.
     *   - DoorBluetoothClient - listen for outgoing events, send a bluetooth message to the other node if it is within
     *      range. Note node address will be stored in DoorNode table.
     *
     * DoorWrapper will setup listeners (e.g. for new outgoingreplication entities etc) that will listen for changes and
     * then emit them via _outgoingEvents .
     *
     * The event is "converted" to a NodeEventMessage as/when needed e.g. by the SSE endpoint. The NodeEvent itself
     * contains only the entity table id and primary key(s). The NodeEventMessage contains the actual entity data.
     */
    val outgoingEvents: Flow<List<NodeEvent>> = _outgoingEvents.asSharedFlow()


    private val _incomingMessages  = MutableSharedFlow<NodeEventMessage>()

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
    val incomingMessages: Flow<NodeEventMessage> = _incomingMessages.asSharedFlow()


    init {

    }

    /**
     * This function is called by the above-mentioned components (HTTP Server, HTTP Client, Bluetooth) when an incoming
     * event is received, which then emits it on the incomingMessages flow for handling.
     *
     * Note: if this event contains incoming replication, we need to do the incoming replication insertion in this
     * function (if something goes wrong, we must throw an exception)
     *
     */
    suspend fun onIncomingEventReceived(event: NodeEventMessage) {
        try {
            db.insertIntoRemoteReceiveView(event.fromNode, event.replications)
            _incomingMessages.emit(event)
        }catch(e: Exception){
            throw e
        }
    }

}