package com.ustadmobile.door.nodeevent

import app.cash.turbine.test
import com.ustadmobile.door.DoorDatabaseWrapper
import com.ustadmobile.door.ext.withDoorTransactionAsync
import com.ustadmobile.door.message.DoorMessage
import com.ustadmobile.door.replication.selectDoorReplicationEntitiesForEvents
import com.ustadmobile.door.test.AbstractCommonTest
import com.ustadmobile.door.test.initNapierLog
import com.ustadmobile.door.test.makeExample3Database
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

/**
 * Integration test to check that:
 *
 * 1) Inserting into OutgoingReplication will generate a DoorNodeEvent
 * 2) The ReplicateEntity for the DoorNodeEvent can be selected as expected
 * 3) Calling onIncomingMessageReceived on another database with the given ReplicateEntity will insert into the other
 *    database
 */
@OptIn(ExperimentalCoroutinesApi::class) //Requires using the real clock, not virtual clock for coroutines.
class NodeEventIntegrationTest : AbstractCommonTest() {

    @Test
    fun givenEmptyDatabases_whenNewOutgoingReplicationInsertedIsEmittedAndMessageIsSentToSecondDatabase_thenEntityIsPresentOnSecondDatabase() = runTest {
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            val db1 = makeExample3Database(1L)

            val db2 = makeExample3Database(2L)

            val outgoingNodeEvents = (db1 as DoorDatabaseWrapper<*>).nodeEventManager.outgoingEvents
            outgoingNodeEvents.filter {
                it.isNotEmpty() && it.first().what == DoorMessage.WHAT_REPLICATION_PUSH
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
                        what = DoorMessage.WHAT_REPLICATION_PUSH,
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
        initNapierLog()
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            val db = makeExample3Database(1L)

            val outgoingNodeEvents = (db as DoorDatabaseWrapper<*>).nodeEventManager.outgoingEvents
            outgoingNodeEvents.filter {
                it.isNotEmpty() && it.first().what == DoorMessage.WHAT_REPLICATION_PUSH
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
                println("Received nodeEvent for $insertedUid")
                cancelAndIgnoreRemainingEvents()
            }

            db.close()
        }
    }

}