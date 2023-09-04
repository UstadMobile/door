package com.ustadmobile.door.replication

import com.ustadmobile.door.message.DoorMessage
import com.ustadmobile.door.nodeevent.NodeEventManager
import io.ktor.client.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Test
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import org.mockito.kotlin.*
import java.util.concurrent.TimeUnit
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DoorRepositoryReplicationClientTest {

    private val json = Json {
        encodeDefaults = true
    }

    private lateinit var mockServer: MockWebServer

    private lateinit var httpClient: HttpClient

    private lateinit var scope: CoroutineScope

    private val outgoingRepUid = 123L

    private val pendingReplicationMessage = DoorMessage(
        what = DoorMessage.WHAT_REPLICATION,
        fromNode = 1L,
        toNode = 2L,
        replications = listOf(
            DoorReplicationEntity(
                tableId = 42,
                orUid = outgoingRepUid,
                entity = buildJsonObject {
                    put("uid", JsonPrimitive(100))
                    put("name", JsonPrimitive("bob"))
                }
            )
        )
    )

    @BeforeTest
    fun setup() {
        mockServer = MockWebServer().also {
            it.start()
        }
        httpClient = HttpClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json(json = json)
            }
        }
        scope = CoroutineScope(Dispatchers.Default + Job())
    }

    fun tearDown() {
        mockServer.shutdown()
        httpClient.close()
        scope.cancel()
    }

    @Test
    fun givenEntitiesArePending_whenClientInitialized_thenShouldRequestPendingReplications() {
        mockServer.enqueue(MockResponse()
            .setHeader("content-type", "application/json")
            .setBody(json.encodeToString(DoorMessage.serializer(), pendingReplicationMessage)))
        mockServer.enqueue(MockResponse()
            .setResponseCode(204)
        )

        val mockEventManager = mock<NodeEventManager<*>> { }
        val repoClient = DoorRepositoryReplicationClient(
            httpClient = httpClient,
            repoEndpointUrl = mockServer.url("/").toString(),
            scope = scope,
            nodeEventManager = mockEventManager,
        )

        verifyBlocking(mockEventManager, timeout(5000)) {
            onIncomingMessageReceived(eq(pendingReplicationMessage))
        }
        val request1 = mockServer.takeRequest()
        assertEquals("/replication/ackAndGetPendingReplications", request1.path)
        assertEquals("post", request1.method?.lowercase())

        val request2 = mockServer.takeRequest(5, TimeUnit.SECONDS)
        assertEquals("/replication/ackAndGetPendingReplications", request2?.path)
        val request2Ack: ReplicationReceivedAck = json.decodeFromString(request2?.body?.readString(Charsets.UTF_8)!!)
        assertTrue(outgoingRepUid in request2Ack.replicationUids)

        repoClient.close()
    }

    @Test
    fun givenNoEntitiesPending_whenClientReceivesInvalidation_thenShouldRequestPendingEntities() {
        mockServer.enqueue(MockResponse()
            .setResponseCode(204))
        mockServer.enqueue(MockResponse()
            .setHeader("content-type", "application/json")
            .setBody(json.encodeToString(DoorMessage.serializer(), pendingReplicationMessage)))
        mockServer.enqueue(MockResponse()
            .setResponseCode(204))

        val mockEventManager = mock<NodeEventManager<*>> { }
        val repoClient = DoorRepositoryReplicationClient(
            httpClient = httpClient,
            repoEndpointUrl = mockServer.url("/").toString(),
            scope = scope,
            nodeEventManager = mockEventManager,
        )

        val request1 = mockServer.takeRequest(5, TimeUnit.SECONDS)
        assertEquals("/replication/ackAndGetPendingReplications", request1?.path)
        assertEquals("post", request1?.method?.lowercase())

        repoClient.invalidate()
        val request2 = mockServer.takeRequest(5, TimeUnit.SECONDS)
        assertEquals("/replication/ackAndGetPendingReplications", request2?.path)
        verifyBlocking(mockEventManager, timeout(5000)) {
            onIncomingMessageReceived(eq(pendingReplicationMessage))
        }
        val request3 = mockServer.takeRequest(5, TimeUnit.SECONDS)
        assertEquals("/replication/ackAndGetPendingReplications", request3?.path)
        assertEquals("post", request3?.method?.lowercase())
        val request3Ack: ReplicationReceivedAck = json.decodeFromString(request3?.body?.readString(Charsets.UTF_8)!!)
        assertTrue(outgoingRepUid in request3Ack.replicationUids)
        repoClient.close()
    }



}