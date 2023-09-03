package com.ustadmobile.door.ktor.routes

import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.door.DoorConstants
import com.ustadmobile.door.ext.withDoorTransactionAsync
import com.ustadmobile.door.message.DoorMessage
import com.ustadmobile.door.replication.ReplicationReceivedAck
import db3.ExampleDb3
import db3.ExampleEntity3
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.gson.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.testing.*
import org.junit.Test
import io.ktor.server.application.install
import io.ktor.server.config.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.assertEquals

class ReplicationRouteTest {

    @Test
    fun givenReplicationsArePendingForNode_whenAckAndGetPendingReplicationsCalled_thenShouldReturnPendingReplications() {
        val db = DatabaseBuilder.databaseBuilder(ExampleDb3::class, "jdbc:sqlite::memory:", 1L)
            .build()
        db.clearAllTables()
        val remoteNodeId = 123L
        val json = Json {
            encodeDefaults = true
        }

        val insertedUid = runBlocking {
            db.withDoorTransactionAsync {
                val uid = db.exampleEntity3Dao.insertAsync(ExampleEntity3())
                db.exampleEntity3Dao.insertOutgoingReplication(uid, remoteNodeId)
                uid
            }
        }


        testApplication {
            environment {
                config = MapApplicationConfig("ktor.environment" to "test")
            }

            val client = createClient {
                install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                    gson()
                }
            }

            application {
                install(ContentNegotiation) {
                    gson {
                        register(ContentType.Application.Json, GsonConverter())
                        register(ContentType.Any, GsonConverter())
                    }
                }

                routing {
                    ReplicationRoute(json) { db }
                }
            }

            val response = client.post("/ackAndGetPendingReplications") {
                header(DoorConstants.HEADER_NODE, "${remoteNodeId}/secret")
                contentType(ContentType.Application.Json)
                setBody(ReplicationReceivedAck(emptyList()))
            }

            val responseDoorMessage: DoorMessage = json.decodeFromString(response.bodyAsText())
            assertEquals(DoorMessage.WHAT_REPLICATION, responseDoorMessage.what)
            assertEquals(1, responseDoorMessage.replications.size)
            val receivedReplicationEntity = responseDoorMessage.replications.first()
            val entityInResponse = json.decodeFromJsonElement(ExampleEntity3.serializer(),
                receivedReplicationEntity.entity)
            val entityInDatabase = db.exampleEntity3Dao.findByUid(insertedUid)
            assertEquals(entityInDatabase, entityInResponse)
        }
    }

}