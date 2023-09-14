package com.ustadmobile.door.ktor.routes

import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.door.DoorConstants
import com.ustadmobile.door.RepositoryConfig
import com.ustadmobile.door.ext.doorWrapperNodeId
import com.ustadmobile.door.ext.getOrThrow
import com.ustadmobile.door.ext.withDoorTransactionAsync
import com.ustadmobile.door.http.DoorHttpServerConfig
import com.ustadmobile.door.message.DoorMessage
import com.ustadmobile.door.replication.DoorReplicationEntity
import com.ustadmobile.door.replication.ReplicationReceivedAck
import com.ustadmobile.door.replication.ServerSentEventsReplicationClient.Companion.EVT_INIT
import com.ustadmobile.door.replication.ServerSentEventsReplicationClient.Companion.EVT_PENDING_REPLICATION
import com.ustadmobile.door.sse.DoorEventListener
import com.ustadmobile.door.sse.DoorEventSource
import com.ustadmobile.door.sse.DoorServerSentEvent
import com.ustadmobile.door.util.systemTimeInMillis
import db3.ExampleDb3
import db3.ExampleEntity3
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.gson.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.config.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.OkHttpClient
import org.junit.Test
import java.net.URLEncoder
import kotlin.test.assertEquals

class ReplicationRouteTest {

    data class ReplicationRouteTestContext(
        val db: ExampleDb3,
        val json: Json,
        val client: HttpClient
    )

    private val serverNodeId = 8042L

    private val clientNodeId = 123L


    private fun testReplicationRoute(
        block: suspend ApplicationTestBuilder.(ReplicationRouteTestContext) -> Unit
    ) {
        val db = DatabaseBuilder.databaseBuilder(ExampleDb3::class, "jdbc:sqlite::memory:", serverNodeId)
            .build()
        db.clearAllTables()

        val json = Json {
            encodeDefaults = true
        }

        val serverConfig = DoorHttpServerConfig(json)

        testApplication {
            environment {
                config = MapApplicationConfig("ktor.environment" to "test")
            }


            @Suppress("RemoveRedundantQualifierName", "RedundantSuppression") //Ensure clarity between client and server
            install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
                gson {
                    register(ContentType.Application.Json, GsonConverter())
                    register(ContentType.Any, GsonConverter())
                }
            }

            routing {
                ReplicationRoute(serverConfig) { db }
            }

            val httpClient = createClient {
                @Suppress("RemoveRedundantQualifierName", "RedundantSuppression") //Ensure clarity between client and server
                install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                    json(json = json)
                }
            }

            block(
                try {
                    ReplicationRouteTestContext(db, json, httpClient)
                }finally {
                    db.close()
                }
            )
        }

    }

    @Test
    fun givenReplicationsArePendingForNode_whenAckAndGetPendingReplicationsCalled_thenShouldReturnPendingReplications() {
        testReplicationRoute {context ->
            val insertedUid = runBlocking {
                context.db.withDoorTransactionAsync {
                    val uid = context.db.exampleEntity3Dao.insertAsync(ExampleEntity3(
                        lastUpdatedTime = systemTimeInMillis()
                    ))
                    context.db.exampleEntity3Dao.insertOutgoingReplication(uid, clientNodeId)
                    uid
                }
            }

            val response = context.client.post("/ackAndGetPendingReplications") {
                header(DoorConstants.HEADER_NODE_AND_AUTH, "${clientNodeId}/secret")
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
            val insertedUid = runBlocking {
                context.db.withDoorTransactionAsync {
                    val uid = context.db.exampleEntity3Dao.insertAsync(ExampleEntity3(
                        lastUpdatedTime = systemTimeInMillis()
                    ))
                    context.db.exampleEntity3Dao.insertOutgoingReplication(uid, clientNodeId)
                    uid
                }
            }

            val response1 = context.client.post("/ackAndGetPendingReplications") {
                header(DoorConstants.HEADER_NODE_AND_AUTH, "${clientNodeId}/secret")
                contentType(ContentType.Application.Json)
                setBody(ReplicationReceivedAck(emptyList()))
            }

            val responseDoorMessage: DoorMessage = context.json.decodeFromString(response1.bodyAsText())
            assertEquals(insertedUid,
                responseDoorMessage.replications.first().entity.getOrThrow("eeUid").jsonPrimitive.long)

            val response2 = context.client.post("/ackAndGetPendingReplications") {
                header(DoorConstants.HEADER_NODE_AND_AUTH, "${clientNodeId}/secret")
                contentType(ContentType.Application.Json)
                setBody(ReplicationReceivedAck(responseDoorMessage.replications.map { it.orUid }))
            }
            assertEquals(HttpStatusCode.NoContent, response2.status)
        }
    }

    @Test
    fun givenEmptyDatabase_whenIncomingMessageWithReplicationReceived_thenShouldBeInserted() {
        testReplicationRoute { context ->
            val exampleEntity = ExampleEntity3(
                eeUid = 1042,
                lastUpdatedTime = systemTimeInMillis(), //Required due do the trigger condition
            )

            val outgoingReplicationUid = 10420L

            val incomingMessage = DoorMessage(
                what = DoorMessage.WHAT_REPLICATION,
                fromNode =123L,
                toNode = context.db.doorWrapperNodeId,
                replications = listOf(
                    DoorReplicationEntity(
                        tableId = ExampleEntity3.TABLE_ID,
                        orUid = outgoingReplicationUid,
                        entity = context.json.encodeToJsonElement(
                            ExampleEntity3.serializer(), exampleEntity
                        ).jsonObject
                    )
                )
            )

            val response = context.client.post("/message") {
                header(DoorConstants.HEADER_NODE_AND_AUTH, "${clientNodeId}/secret")
                contentType(ContentType.Application.Json)
                setBody(incomingMessage)
            }

            val receivedAck: ReplicationReceivedAck = response.body()
            assertEquals(outgoingReplicationUid, receivedAck.replicationUids.first())

            val entityInDb = context.db.exampleEntity3Dao.findByUid(exampleEntity.eeUid)
            assertEquals(exampleEntity, entityInDb)
        }
    }

    /**
     * Test that if there is a new pending replication whilst a client is connected to the Server Sent Events endpoint
     * that it will receive an EVT_PENDING_REPLICATION event.
     *
     * This has to be done using a real server because the Ktor Http client does not support server sent events.
     *
     */
    @Test
    fun givenServerSentEventsClientConnected_whenNewOutgoingReplicationIsPending_thenWillReceiveEvent() {
        val db = DatabaseBuilder.databaseBuilder(ExampleDb3::class, "jdbc:sqlite::memory:", serverNodeId)
            .build()
        db.clearAllTables()

        val json = Json {
            encodeDefaults = true
        }
        val serverConfig = DoorHttpServerConfig(json)

        val okHttpClient = OkHttpClient.Builder().build()
        val httpClient = HttpClient {  }
        val repoConfig = RepositoryConfig.repositoryConfig(Any(), "http://localhost:8094", clientNodeId,
                "secret", httpClient, okHttpClient)

        val server = embeddedServer(Netty, 8094) {
            routing {
                ReplicationRoute(serverConfig) { db }
            }
        }
        server.start()
        val url = "http://localhost:8094/sse?door-node=${URLEncoder.encode("$clientNodeId/secret", "UTF-8")}"

        val eventChannel = Channel<DoorServerSentEvent>(capacity = Channel.UNLIMITED)
        val listener = object: DoorEventListener {
            override fun onOpen() {

            }

            override fun onMessage(message: DoorServerSentEvent) {
                eventChannel.trySend(message)
            }

            override fun onError(e: Exception) {

            }
        }

        val serverSentEventsClient = DoorEventSource(
            repoConfig = repoConfig,
            url = url,
            listener = listener,
            retry = 1000
        )

        try {
            runBlocking {
                val initEvt = withTimeout(500000) { eventChannel.receive() }
                assertEquals(EVT_INIT, initEvt.event)

                db.withDoorTransactionAsync {
                    val uid = db.exampleEntity3Dao.insertAsync(
                        ExampleEntity3(
                            lastUpdatedTime = systemTimeInMillis()
                        )
                    )
                    db.exampleEntity3Dao.insertOutgoingReplication(uid, clientNodeId)
                }

                val replicationPendingEvt = withTimeout(5000) { eventChannel.receive() }
                assertEquals(EVT_PENDING_REPLICATION, replicationPendingEvt.event)
            }
        }finally {
            server.stop()
            db.close()
            serverSentEventsClient.close()
            httpClient.close()
        }
    }


}