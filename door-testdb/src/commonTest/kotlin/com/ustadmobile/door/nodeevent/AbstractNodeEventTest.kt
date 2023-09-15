package com.ustadmobile.door.nodeevent

import app.cash.turbine.test
import com.ustadmobile.door.DoorDatabaseWrapper
import com.ustadmobile.door.ext.withDoorTransactionAsync
import com.ustadmobile.door.message.DoorMessage
import com.ustadmobile.door.replication.selectDoorReplicationEntitiesForEvents
import db3.ExampleDb3
import db3.ExampleEntity3
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class) //Requires using the real clock, not virtual clock for coroutines.
abstract class AbstractNodeEventTest {


    abstract fun makeExampleDb(nodeId: Long): ExampleDb3

    @Test
    fun givenEmptyDatabases_whenNewOutgoingReplicationInsertedIsEmittedAndMessageIsSentToSecondDatabase_thenEntityIsPresentOnSecondDatabase() = runTest {
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            val db1 = makeExampleDb(1L)

            val db2 = makeExampleDb(2L)

            val outgoingNodeEvents = (db1 as DoorDatabaseWrapper<*>).nodeEventManager.outgoingEvents
            outgoingNodeEvents.filter {
                it.isNotEmpty() && it.first().what == DoorMessage.WHAT_REPLICATION
            }.test(timeout = 10.seconds) {
                val insertedUid = db1.withDoorTransactionAsync {
                    val uid = db1.exampleEntity3Dao.insertAsync(ExampleEntity3())
                    db1.exampleEntity3Dao.insertOutgoingReplication(uid, 123L)
                    uid
                }

                val replicationEvent = awaitItem().first()

                val eventReplication = db1
                    .selectDoorReplicationEntitiesForEvents(listOf(replicationEvent))
                    .first()

                (db2 as DoorDatabaseWrapper<*>).nodeEventManager.onIncomingMessageReceived(
                    DoorMessage(
                        what = DoorMessage.WHAT_REPLICATION,
                        fromNode = 1L,
                        toNode = 2L,
                        replications = listOf(eventReplication)
                    )
                )

                val entityInDb2 = db2.exampleEntity3Dao.findByUid(insertedUid)
                assertNotNull(entityInDb2)
                cancelAndIgnoreRemainingEvents()
            }
            db1.close()
            db2.close()
        }
    }

    /**
     *
     */
    @Test
    fun givenEmptyDatabase_whenNewOutgoingReplicationInserted_thenShouldEmitNodeEventWhichCanBeSelectedAsReplicationEntity()  = runTest {
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            val db = makeExampleDb(1L)

            val outgoingNodeEvents = (db as DoorDatabaseWrapper<*>).nodeEventManager.outgoingEvents
            outgoingNodeEvents.filter {
                it.isNotEmpty() && it.first().what == DoorMessage.WHAT_REPLICATION
            }.test(timeout = 10.seconds) {
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

                val eventReplication = db.selectDoorReplicationEntitiesForEvents(nodeEvents).first()
                assertEquals(ExampleEntity3.TABLE_ID, eventReplication.tableId)
                assertEquals(insertedUid, eventReplication.entity.get("eeUid")?.jsonPrimitive?.long)

                cancelAndIgnoreRemainingEvents()
            }

            db.close()
        }
    }

}