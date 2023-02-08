package com.ustadmobile.lib.annotationprocessor.core.replication

import com.ustadmobile.door.ChangeListenerRequest
import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.door.entities.DoorNode
import com.ustadmobile.door.replication.ReplicationNotificationDispatcher
import com.ustadmobile.door.replication.ReplicationPendingListener
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Test
import repdb.RepDb
import repdb.RepDb_ReplicationRunOnChangeRunner
import repdb.RepEntity
import org.mockito.kotlin.*


class RepTest1 {

    @Test
    fun givenKnownRemoteNode_whenReplicatableEntityInserted_thenShouldInsertTrackerAndFirePendingNotificationEvent() {
        val db = DatabaseBuilder.databaseBuilder(RepDb::class, "jdbc:sqlite:build/tmp/repdb.sqlite").build()
            .apply {
                clearAllTables()
            }

        val dispatcher = ReplicationNotificationDispatcher(db,
            RepDb_ReplicationRunOnChangeRunner(db), GlobalScope)

        db.invalidationTracker.addObserver(dispatcher)

        val repPendingListener = mock<ReplicationPendingListener> { }

        runBlocking {
            dispatcher.addReplicationPendingEventListener(42, repPendingListener)

            db.repDao.insertDoorNodeAsync(DoorNode().apply {
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

    @Test
    fun givenModificationMadeBeforeChangeListenerAdded_whenChangeListenerAdded_thenShouldGetPendingNotification() {
        val db = DatabaseBuilder.databaseBuilder( RepDb::class, "jdbc:sqlite:build/tmp/repdb.sqlite").build()
            .apply {
                clearAllTables()
            }

        val dispatcher = ReplicationNotificationDispatcher(db,
            RepDb_ReplicationRunOnChangeRunner(db), GlobalScope)
        db.invalidationTracker.addObserver(dispatcher)


        val repPendingListener = mock<ReplicationPendingListener> { }

        runBlocking {
            db.repDao.insertDoorNodeAsync(DoorNode().apply {
                this.nodeId = 42
                this.auth = "magic"
            })

            db.repDao.insertAsync(RepEntity().apply {
                this.reNumField = 52
            })

            //Delay longer than the collation wait value used in ReplicationNotificationDispatcher
            delay(400)

            dispatcher.addReplicationPendingEventListener(42, repPendingListener)
        }

        verify(repPendingListener, timeout(5000 * 5000)).onReplicationPending(argWhere {
            it.nodeId == 42L && RepEntity.TABLE_ID in it.tableIds
        })
    }

}