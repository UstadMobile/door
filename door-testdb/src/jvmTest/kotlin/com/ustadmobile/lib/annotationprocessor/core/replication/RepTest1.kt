package com.ustadmobile.lib.annotationprocessor.core.replication

import com.ustadmobile.door.ChangeListenerRequest
import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.door.entities.DoorNode
import com.ustadmobile.door.replication.ReplicationNotificationDispatcher
import com.ustadmobile.door.replication.ReplicationPendingListener
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.runBlocking
import org.junit.Test
import repdb.RepDb
import repdb.RepDb_ReplicationRunOnChangeRunner
import repdb.RepEntity
import org.mockito.kotlin.*


class RepTest1 {

    @Test
    fun givenKnownRemoteNode_whenReplicatableEntityInserted_thenShouldInsertTrackerAndFirePendingNotificationEvent() {
        val db = DatabaseBuilder.databaseBuilder(Any(), RepDb::class, "RepDb").build()
            .apply {
                clearAllTables()
            }

        val dispatcher = ReplicationNotificationDispatcher(db,
            RepDb_ReplicationRunOnChangeRunner(db, db), GlobalScope)
        db.addChangeListener(ChangeListenerRequest(listOf("RepEntity"), dispatcher))

        val repPendingListener = mock<ReplicationPendingListener> { }

        runBlocking {
            dispatcher.addReplicationPendingEventListener(42, repPendingListener)

            db.repDao.insertDoorNode(DoorNode().apply {
                this.nodeId = 42
                this.auth = "magic"
            })

            db.repDao.insertAsync(RepEntity().apply {
                this.reNumField = 52
            })

        }

        verify(repPendingListener, timeout(10000)).onReplicationPending(argWhere {
            it.nodeId == 42L && RepEntity.TABLE_ID in it.tableIds
        })
    }

}