package com.ustadmobile.door.replication

import com.ustadmobile.door.sse.DoorServerSentEvent

class ReplicationPendingEvent(val nodeId: Long, val tableIds: List<Int>) {

    fun toDoorServerSentEvent(evtId: String) = DoorServerSentEvent(evtId,
        ReplicationSubscriptionManager.EVT_INVALIDATE, tableIds.joinToString())

}