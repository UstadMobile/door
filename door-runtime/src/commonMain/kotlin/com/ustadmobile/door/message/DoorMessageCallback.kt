package com.ustadmobile.door.message

import com.ustadmobile.door.room.RoomDatabase

/**
 * This callback can be used to implement custom logic when incoming replication data is being received or sent.
 */
interface DoorMessageCallback<T: RoomDatabase> {

    /**
     * OnMessageReceived will be invoked before any processing of incoming replication
     * data (inserting ReplicateEntities that use a INSERT_DIRECT or INSERT_INTO_VIEW strategy) that results from a
     * pull or push. This is called within the context of a transaction before any changes are made.
     *
     * The callback can be used to implement custom logic to handle incoming replications.
     *
     * @param db the database
     * @param eventMessage the message that has been received from another node
     * @return the DoorMessage to process e.g. the callback can modify the message if desired
     */
    suspend fun onIncomingMessageReceived(
        db: T,
        eventMessage: DoorMessage,
    ): DoorMessage

    /**
     * OnMessageProcessed will be called after processing of incoming replication data (inserting ReplicateEntities
     * that use a INSERT_DIRECT or INSERT_INTO_VIEW strategy) that results from a pull or push before the transaction
     * is committed.
     *
     * @param db the database
     * @param eventMessage the message that has been received and processed from another node
     *
     */
    suspend fun onIncomingMessageProcessed(
        db: T,
        eventMessage: DoorMessage,
    )

    /**
     * OnBeforeMessageSend will be called before a message is sent to another node.
     *
     * @param db the database
     * @param eventMessage the message that is about to be sent to another node
     *
     * @return the DoorMessage to send to the other node. The to node must NOT be changed.
     */
    suspend fun onBeforeOutgoingMessageSend(
        db: T,
        eventMessage: DoorMessage,
    ): DoorMessage

    /**
     * OnMessageSent is called when an outgoing message is successfully sent to another node.
     * @param db the database
     * @param eventMessage the message that was successfully received by the other node
     */
    suspend fun onOutgoingMessageSent(
        db: T,
        eventMessage: DoorMessage,
    )
}
