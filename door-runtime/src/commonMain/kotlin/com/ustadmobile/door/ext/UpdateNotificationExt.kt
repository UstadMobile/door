package com.ustadmobile.door.ext

import com.ustadmobile.door.entities.UpdateNotification
import com.ustadmobile.door.sse.DoorServerSentEvent

/**
 * Convert the given UpdateNotification into a DoorServerSentEvent
 */
fun UpdateNotification.asDoorServerSentEvent() : DoorServerSentEvent {
    return DoorServerSentEvent(id = pnUid.toString(), event = "UPDATE",
            data = "${pnTableId} ${pnTimestamp}")
}