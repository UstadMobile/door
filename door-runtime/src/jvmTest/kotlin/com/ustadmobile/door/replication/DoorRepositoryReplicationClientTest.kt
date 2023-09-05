package com.ustadmobile.door.replication

import app.cash.turbine.test
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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.RecordedRequest
import org.mockito.kotlin.*
import kotlin.test.BeforeTest
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds

class DoorRepositoryReplicationClientTest {

    private val json = Json {
        encodeDefaults = true
    }

    private lateinit var mockServer: MockWebServer

    private lateinit var httpClient: HttpClient

    private lateinit var scope: CoroutineScope

    private val outgoingRepUid = 123L

    private val clientNodeId = 1L

    private val serverNodeId = 2L

    private val pendingReplicationMessage = DoorMessage(
        what = DoorMessage.WHAT_REPLICATION,
        fromNode = clientNodeId,
        toNode = serverNodeId,
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

    /**
     * When the client receives an invalidation it should make another request to the server for any pending outgoing
     * replications it has that are destined for this node. When there are entities they should be fetched and delivered
     * to the NodeEventManager
     */
    @Test
    fun givenIncomingEntitiesArePending_whenClientInitialized_thenShouldRequestPendingReplications() {
        var ackAndGetPendingRequestCount = 0
        val ackRequests = MutableSharedFlow<RecordedRequest>(replay = 10)
        mockServer.dispatcher = object: Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if(request.path == "/replication/ackAndGetPendingReplications") {
                    ackRequests.tryEmit(request)
                    ackAndGetPendingRequestCount++
                    return if(ackAndGetPendingRequestCount <= 1)
                        MockResponse()
                            .setHeader("content-type", "application/json")
                            .setBody(json.encodeToString(DoorMessage.serializer(), pendingReplicationMessage))
                    else
                        MockResponse().setResponseCode(204)
                }

                return MockResponse().setResponseCode(404)
            }
        }

        val mockEventManager = mock<NodeEventManager<*>> { }
        val repoClient = DoorRepositoryReplicationClient(
            localNodeId = clientNodeId,
            httpClient = httpClient,
            repoEndpointUrl = mockServer.url("/").toString(),
            scope = scope,
            nodeEventManager = mockEventManager,
            onMarkAcknowledgedAndGetNextOutgoingReplications = mock { },
        )

        verifyBlocking(mockEventManager, timeout(5000)) {
            onIncomingMessageReceived(eq(pendingReplicationMessage))
        }

        runBlocking {
            ackRequests.filter {
                it.path == "/replication/ackAndGetPendingReplications" &&
                        outgoingRepUid in json.decodeFromString<ReplicationReceivedAck>(it.body.readString(Charsets.UTF_8))
                            .replicationUids
            }.test(name = "Acknowledgement was sent to server", timeout = 5.seconds) {
                assertNotNull(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

        repoClient.close()
    }

    /**
     * When the client receives an invalidation it should make another request to the server for any pending outgoing
     * replications it has that are destined for this node. If there is nothing initially, another request should be
     * made when the invalidate function is called.
     */
    @Test(timeout = 10000)
    fun givenNoIncomingEntitiesPending_whenClientReceivesInvalidation_thenShouldRequestPendingEntities() {
        var ackAndGetPendingRequestCount = 0
        val ackRequests = MutableSharedFlow<RecordedRequest>(replay = 10)
        mockServer.dispatcher = object: Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return if(request.path == "/replication/ackAndGetPendingReplications") {
                    ackAndGetPendingRequestCount++
                    ackRequests.tryEmit(request)
                    if(ackAndGetPendingRequestCount == 2) {
                        MockResponse()
                            .setHeader("content-type", "application/json")
                            .setBody(json.encodeToString(DoorMessage.serializer(), pendingReplicationMessage))
                    }else {
                        MockResponse().setResponseCode(204)
                    }
                }else {
                    MockResponse().setResponseCode(404)
                }
            }
        }

        val mockEventManager = mock<NodeEventManager<*>> { }
        val repoClient = DoorRepositoryReplicationClient(
            localNodeId = clientNodeId,
            httpClient = httpClient,
            repoEndpointUrl = mockServer.url("/").toString(),
            scope = scope,
            nodeEventManager = mockEventManager,
            onMarkAcknowledgedAndGetNextOutgoingReplications = mock { },
        )

        //wait for the first request
        runBlocking { ackRequests.filter { it.path == "/replication/ackAndGetPendingReplications" }.first() }

        repoClient.invalidate()

        verifyBlocking(mockEventManager, timeout(5000)) {
            onIncomingMessageReceived(eq(pendingReplicationMessage))
        }

        runBlocking {
            ackRequests.filter {
                it.path == "/replication/ackAndGetPendingReplications" &&
                        outgoingRepUid in json.decodeFromString<ReplicationReceivedAck>(it.body.readString(Charsets.UTF_8)).replicationUids
            }.test(timeout = 5.seconds, name = "") {
                assertNotNull(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

        repoClient.close()
    }

    @Test
    fun givenOutgoingEntitiesPending_whenClientReceivesNodeEvent_thenShouldSendPendingOutgoingEntities() {

    }




}