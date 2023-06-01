package com.ustadmobile.lib.annotationprocessor.core

import app.cash.turbine.test
import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.door.DoorDatabaseWrapper
import com.ustadmobile.door.ext.withDoorTransactionAsync
import com.ustadmobile.door.nodeevent.NodeEventMessage.Companion.WHAT_REPLICATION
import db3.ExampleDb3
import db3.ExampleEntity3
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class NodeEventTest {

    /**
     *
     */
    @Test
    fun givenEmptyDatabase_whenNewOutgoingReplicationInserted_thenShouldEmitNodeEvent() {
        val db = DatabaseBuilder.databaseBuilder(ExampleDb3::class, "jdbc:sqlite::memory:")
            .build()
        db.clearAllTables()

        runBlocking {
            val outgoingNodeEvents = (db as DoorDatabaseWrapper).nodeEventManager.outgoingEvents
            outgoingNodeEvents.filter {
                it.what == WHAT_REPLICATION
            }.test(timeout = 5.seconds) {
                val insertedUid = db.withDoorTransactionAsync {
                    val uid = db.exampleEntity3Dao.insertAsync(ExampleEntity3())
                    db.exampleEntity3Dao.insertOutgoingReplication(uid, 123L)
                    uid
                }

                val replicationEvent = awaitItem()
                assertEquals(123L, replicationEvent.toNode)
                assertEquals(insertedUid, replicationEvent.key1)
                assertEquals(ExampleEntity3.TABLE_ID, replicationEvent.tableId)
                cancelAndIgnoreRemainingEvents()
            }
        }

    }

}