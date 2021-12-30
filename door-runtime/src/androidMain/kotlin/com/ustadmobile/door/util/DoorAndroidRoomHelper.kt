package com.ustadmobile.door.util

import com.ustadmobile.door.DoorDatabase
import com.ustadmobile.door.IncomingReplicationListenerHelper
import com.ustadmobile.door.ext.dbClassName
import com.ustadmobile.door.replication.ReplicationNotificationDispatcher
import com.ustadmobile.door.replication.ReplicationRunOnChangeRunner
import kotlinx.coroutines.GlobalScope

class DoorAndroidRoomHelper(val db: DoorDatabase) {

    val incomingReplicationListenerHelper = IncomingReplicationListenerHelper()

    val replicationNotificationDispatcher : ReplicationNotificationDispatcher by lazy {
        val dbClass = Class.forName(db.dbClassName)
        val dbClassName = db.dbClassName
        val runOnChangeRunnerClass = Class.forName("${dbClassName}_ReplicationRunOnChangeRunner")
            .getConstructor(dbClass)
            .newInstance(db) as ReplicationRunOnChangeRunner
        ReplicationNotificationDispatcher(db, runOnChangeRunnerClass, GlobalScope)
    }

    val nodeIdAndAuthCache: NodeIdAuthCache by lazy {
        NodeIdAuthCache(db)
    }

}