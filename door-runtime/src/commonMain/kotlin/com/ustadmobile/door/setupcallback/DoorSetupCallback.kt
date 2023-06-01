package com.ustadmobile.door.setupcallback

import com.ustadmobile.door.DoorDatabaseCallbackStatementList
import com.ustadmobile.door.DoorSqlDatabase
import com.ustadmobile.door.nodeevent.NodeEventMessage
import com.ustadmobile.door.room.RoomDatabase
import kotlin.reflect.KClass

/**
 * On JVM/JDBC: Integrate with RoomHelperJdbc e.g. RoomHelperJdbc can have an internal before/after transaction listener/hook
 * On Android: Setup in the callback, messagemanager listens for invalidation of OutgoingReplication
 */
abstract class DoorSetupCallback(
    private val dbClass: KClass<out RoomDatabase>
) : DoorDatabaseCallbackStatementList {
    override fun onCreate(db: DoorSqlDatabase): List<String> {
        val stmtList = mutableListOf<String>()



        return stmtList
    }

    override fun onOpen(db: DoorSqlDatabase): List<String> {
        val stmtList = mutableListOf<String>()




        return stmtList
    }

    companion object {


    }
}