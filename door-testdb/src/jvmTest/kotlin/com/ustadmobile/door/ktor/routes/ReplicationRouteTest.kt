package com.ustadmobile.door.ktor.routes

import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.door.DoorConstants
import com.ustadmobile.door.ext.getOrThrow
import com.ustadmobile.door.ext.withDoorTransactionAsync
import com.ustadmobile.door.message.DoorMessage
import com.ustadmobile.door.replication.ReplicationReceivedAck
import db3.ExampleDb3
import db3.ExampleEntity3
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.gson.*
import io.ktor.server.testing.*
import org.junit.Test
import io.ktor.server.config.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.assertEquals
import io.ktor.client.HttpClient
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

class ReplicationRouteTest {

    data class ReplicationRouteTestContext(
        val db: ExampleDb3,
        val json: Json,
        val client: HttpClient

    )


    private fun testReplicationRoute(
        block: suspend ApplicationTestBuilder.(ReplicationRouteTestContext) -> Unit
    ) {
        val db = DatabaseBuilder.databaseBuilder(ExampleDb3::class, "jdbc:sqlite::memory:", 1L)
            .build()
        db.clearAllTables()

        val json = Json {
            encodeDefaults = true
        }


        testApplication {
            environment {
                config = MapApplicationConfig("ktor.environment" to "test")
            }


            @Suppress("RemoveRedundantQualifierName")
            install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
                gson {
                    register(ContentType.Application.Json, GsonConverter())
                    register(ContentType.Any, GsonConverter())
                }
            }

            routing {
                ReplicationRoute(json) { db }
            }

            val client = createClient {
                install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                    gson()
                }
            }

            block(
                ReplicationRouteTestContext(db, json, client)
            )
        }

    }

    @Test
    fun givenReplicationsArePendingForNode_whenAckAndGetPendingReplicationsCalled_thenShouldReturnPendingReplications() {
        testReplicationRoute {context ->
            val remoteNodeId = 123L
            val insertedUid = runBlocking {
                context.db.withDoorTransactionAsync {
                    val uid = context.db.exampleEntity3Dao.insertAsync(ExampleEntity3())
                    context.db.exampleEntity3Dao.insertOutgoingReplication(uid, remoteNodeId)
                    uid
                }
            }

            val response = context.client.post("/ackAndGetPendingReplications") {
                header(DoorConstants.HEADER_NODE, "${remoteNodeId}/secret")
                contentType(ContentType.Application.Json)
                setBody(ReplicationReceivedAck(emptyList()))
            }

            val responseDoorMessage: DoorMessage = context.json.decodeFromString(response.bodyAsText())
            assertEquals(DoorMessage.WHAT_REPLICATION, responseDoorMessage.what)
            assertEquals(1, responseDoorMessage.replications.size)
            val receivedReplicationEntity = responseDoorMessage.replications.first()
            val entityInResponse = context.json.decodeFromJsonElement(ExampleEntity3.serializer(),
                receivedReplicationEntity.entity)
            val entityInDatabase = context.db.exampleEntity3Dao.findByUid(insertedUid)
            assertEquals(entityInDatabase, entityInResponse)
        }
    }


    @Test
    fun givenReplicationsPendingForNode_whenReplicationsAreAcknowledged_thenNextResultShouldReturnNoContent() {
        testReplicationRoute { context ->
            val remoteNodeId = 123L
            val insertedUid = runBlocking {
                context.db.withDoorTransactionAsync {
                    val uid = context.db.exampleEntity3Dao.insertAsync(ExampleEntity3())
                    context.db.exampleEntity3Dao.insertOutgoingReplication(uid, remoteNodeId)
                    uid
                }
            }

            val response1 = context.client.post("/ackAndGetPendingReplications") {
                header(DoorConstants.HEADER_NODE, "${remoteNodeId}/secret")
                contentType(ContentType.Application.Json)
                setBody(ReplicationReceivedAck(emptyList()))
            }

            val responseDoorMessage: DoorMessage = context.json.decodeFromString(response1.bodyAsText())
            assertEquals(insertedUid,
                responseDoorMessage.replications.first().entity.getOrThrow("eeUid").jsonPrimitive.long)

            val response2 = context.client.post("/ackAndGetPendingReplications") {
                header(DoorConstants.HEADER_NODE, "${remoteNodeId}/secret")
                contentType(ContentType.Application.Json)
                setBody(ReplicationReceivedAck(responseDoorMessage.replications.map { it.orUid }))
            }
            assertEquals(HttpStatusCode.NoContent, response2.status)
        }
    }

}