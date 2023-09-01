package com.ustadmobile.lib.annotationprocessor.core

import app.cash.turbine.test
import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.door.DoorDatabaseWrapper
import com.ustadmobile.door.ext.withDoorTransactionAsync
import com.ustadmobile.door.message.DoorMessage
import com.ustadmobile.door.message.DoorMessage.Companion.WHAT_REPLICATION
import com.ustadmobile.door.replication.selectNodeEventMessageReplications
import db3.ExampleDb3
import db3.ExampleEntity3
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds

class NodeEventTest {

    /**
     *
     */
    @Test
    fun givenEmptyDatabase_whenNewOutgoingReplicationInserted_thenShouldEmitNodeEventWhichCanBeSelectedAsReplicationEntity() {
        val db = DatabaseBuilder.databaseBuilder(ExampleDb3::class, "jdbc:sqlite::memory:", 1L)
            .build()
        db.clearAllTables()

        runBlocking {
            val outgoingNodeEvents = (db as DoorDatabaseWrapper<*>).nodeEventManager.outgoingEvents
            outgoingNodeEvents.filter {
                it.isNotEmpty() && it.first().what == WHAT_REPLICATION
            }.test(timeout = 500.seconds) {
                val insertedUid = db.withDoorTransactionAsync {
                    val uid = db.exampleEntity3Dao.insertAsync(ExampleEntity3())
                    db.exampleEntity3Dao.insertOutgoingReplication(uid, 123L)
                    uid
                }

                val nodeEvents = awaitItem()
                assertEquals(1, nodeEvents.size)
                assertEquals(123L, nodeEvents.first().toNode)
                assertEquals(insertedUid, nodeEvents.first().key1)
                assertEquals(ExampleEntity3.TABLE_ID, nodeEvents.first().tableId)

                val eventReplication = db.selectNodeEventMessageReplications(nodeEvents).first()
                assertEquals(ExampleEntity3.TABLE_ID, eventReplication.tableId)
                assertEquals(insertedUid, eventReplication.entity.get("eeUid")?.jsonPrimitive?.long)

                cancelAndIgnoreRemainingEvents()
            }
        }

    }

    //Note: fixing this might require changing trigger
    @Test
    fun givenEmptyDatabases_whenNewOutgoingReplicationInsertedIsEmittedAndMessageIsSentToSecondDatabase_thenEntityIsPresentOnSecondDatabase() {
        val db1 = DatabaseBuilder.databaseBuilder(ExampleDb3::class, "jdbc:sqlite::memory:", 1L)
            .build().also {
                it.clearAllTables()
            }

        val db2 = DatabaseBuilder.databaseBuilder(ExampleDb3::class, "jdbc:sqlite::memory:", 2L)
            .build().also {
                it.clearAllTables()
            }

        runBlocking {
            val outgoingNodeEvents = (db1 as DoorDatabaseWrapper<*>).nodeEventManager.outgoingEvents
            outgoingNodeEvents.filter {
                it.isNotEmpty() && it.first().what == WHAT_REPLICATION
            }.test(timeout = 5.seconds) {
                val insertedUid = db1.withDoorTransactionAsync {
                    val uid = db1.exampleEntity3Dao.insertAsync(ExampleEntity3())
                    db1.exampleEntity3Dao.insertOutgoingReplication(uid, 123L)
                    uid
                }

                val replicationEvent = awaitItem().first()

                val eventReplication = db1
                    .selectNodeEventMessageReplications(listOf(replicationEvent))
                    .first()

                (db2 as DoorDatabaseWrapper<*>).nodeEventManager.onIncomingMessageReceived(
                    DoorMessage(
                        what = WHAT_REPLICATION,
                        fromNode = 1L,
                        toNode = 2L,
                        replications = listOf(eventReplication)
                    )
                )

                val entityInDb2 = db2.exampleEntity3Dao.findByUid(insertedUid)
                assertNotNull(entityInDb2)
                cancelAndIgnoreRemainingEvents()
            }
        }
    }


}