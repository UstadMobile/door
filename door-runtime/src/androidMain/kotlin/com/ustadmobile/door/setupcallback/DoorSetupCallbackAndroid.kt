package com.ustadmobile.door.setupcallback

import com.ustadmobile.door.DoorSqlDatabase
import com.ustadmobile.door.nodeevent.NodeEventConstants.CREATE_EVENT_TMP_TABLE_SQL
import com.ustadmobile.door.nodeevent.NodeEventConstants.CREATE_OUTGOING_REPLICATION_EVENT_TRIGGER
import com.ustadmobile.door.room.RoomDatabase
import kotlin.reflect.KClass

class DoorSetupCallbackAndroid(
    dbClass: KClass<out RoomDatabase>
): DoorSetupCallback(dbClass) {
    override fun onOpen(db: DoorSqlDatabase): List<String> {
        return buildList {
            addAll(super.onOpen(db))

            add(CREATE_EVENT_TMP_TABLE_SQL)
            add(CREATE_OUTGOING_REPLICATION_EVENT_TRIGGER)
        }

    }
}