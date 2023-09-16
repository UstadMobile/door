package com.ustadmobile.door.nodeevent

import com.ustadmobile.door.message.DoorMessageCallback
import com.ustadmobile.door.room.RoomDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

class NodeEventManagerJs<T: RoomDatabase>(
    db: T,
    messageCallback: DoorMessageCallback<T>,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : NodeEventManagerCommon<T>(
    db, messageCallback, dispatcher,
) {

}