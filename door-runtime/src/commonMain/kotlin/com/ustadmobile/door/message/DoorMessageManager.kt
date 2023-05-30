package com.ustadmobile.door.message

import com.ustadmobile.door.room.RoomDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * MessageManager manages flows of incoming and outgoing messages. Messages are used to transmit Replications and
 * Invalidations.
 *
 * The MessageManager listens for events/new outgoing replications and emits them on the outgoing messages flow. The
 * outgoing message flow is observed by any/all components that deliver messages e.g. server sent event http endpoints,
 * http repository client, and bluetooth client.
 *
 * Note: RepositoryClient can use server sent events client on http, on bluetooth it can send a heartbeat so that any
 * nearby proxy device knows it is still alive.
 *
 */
class DoorMessageManager(
    private val db: RoomDatabase,
    private val dispatcher: CoroutineDispatcher,
    private val scope: CoroutineScope,
) {

    private val _outgoingMessages = MutableSharedFlow<DoorMessage>()

    /**
     * The flow of outgoing events that need to be delivered to other nodes. This flow will be observed by:
     *   - HTTP ServerSentEvents (SSE) endpoint (used by http server) - listen for events for the client listening, emit an
     *     event
     *   - DoorHttpClient - listen for events for upstream server. When event is received, make an HTTP put request.
     *   - DoorBluetoothClient - listen for outgoing events, send a bluetooth message to the other node if it is within
     *      range. Note node address will be stored in DoorNode table.
     *
     * DoorWrapper will setup listeners (e.g. for new outgoingreplication entities etc) that will listen for changes and
     * then emit them via _outgoingEvents .
     */
    val outgoingMessages: Flow<DoorMessage> = _outgoingMessages.asSharedFlow()


    private val _incomingMessages  = MutableSharedFlow<DoorMessage>()

    /**
     * The flow of incoming events being received from other nodes. This is received as follows:
     *    - HTTP server: received via /receiveEvents endpoint
     *    - HTTP client: received via the server sent events (SSE) stream
     *    - Bluetooth: received via the Bluetooth server socket
     *
     * The flow is observed by:
     *    - IncomingReplicationHandler: runs the insert for the given entity (e.g. insert into receiveview)
     *    - InvalidationHandler (for later): an event could be emitted to indicate that something has been invalidated
     *      e.g. to trigger repository flow invalidation
     */
    val incomingMessages: Flow<DoorMessage> = _incomingMessages.asSharedFlow()

    /**
     * This function is called by the above-mentioned components (HTTP Server, HTTP Client, Bluetooth) when an incoming
     * event is received, which then emits it on the incomingMessages flow for handling.
     */
    internal suspend fun onIncomingEventReceived(event: DoorMessage) {
        _incomingMessages.emit(event)
    }

}