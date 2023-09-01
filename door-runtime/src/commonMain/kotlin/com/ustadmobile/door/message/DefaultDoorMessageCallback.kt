package com.ustadmobile.door.message

import com.ustadmobile.door.room.RoomDatabase

class DefaultDoorMessageCallback<T: RoomDatabase>: DoorMessageCallback<T> {

    override suspend fun onIncomingMessageReceived(db: T, eventMessage: DoorMessage): DoorMessage {
        return eventMessage
    }

    override suspend fun onIncomingMessageProcessed(db: T, eventMessage: DoorMessage) {
        // do nothing
    }

    override suspend fun onBeforeOutgoingMessageSend(db: T, eventMessage: DoorMessage): DoorMessage {
        return eventMessage
    }

    override suspend fun onOutgoingMessageSent(db: T, eventMessage: DoorMessage) {
        // do nothing
    }
}