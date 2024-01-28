package com.ustadmobile.door.replication

import app.cash.turbine.test
import com.ustadmobile.door.DoorConstants
import com.ustadmobile.door.log.DoorLogger
import com.ustadmobile.door.log.NapierDoorLogger
import com.ustadmobile.door.message.DoorMessage
import com.ustadmobile.door.nodeevent.NodeEvent
import com.ustadmobile.door.nodeevent.NodeEventManager
import com.ustadmobile.door.room.RoomDatabase
import io.ktor.client.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Test
import org.mockito.kotlin.*
import java.util.concurrent.atomic.AtomicInteger
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
        what = DoorMessage.WHAT_REPLICATION_PUSH,
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

    class TestEventManager(private val scope: CoroutineScope): NodeEventManager<RoomDatabase> {

        private val _outgoingEvents = MutableSharedFlow<List<NodeEvent>>()

        override val outgoingEvents: Flow<List<NodeEvent>> = _outgoingEvents.asSharedFlow()

        private val _incomingMessages = MutableSharedFlow<DoorMessage>()

        override val incomingMessages: Flow<DoorMessage> = _incomingMessages.asSharedFlow()

        override val dbName: String = "testeventmanagerdb"

        override val logger: DoorLogger = NapierDoorLogger()

        fun emitOutgoingEvents(events: List<NodeEvent>) {
            scope.launch {
                _outgoingEvents.emit(events)
            }
        }

        override suspend fun onIncomingMessageReceived(message: DoorMessage) {
            _incomingMessages.emit(message)
        }

    }

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

        val mockEventManager = spy<NodeEventManager<*>>(TestEventManager(scope))
        val repoClient = DoorRepositoryReplicationClient(
            localNodeId = clientNodeId,
            localNodeAuth = "secret",
            httpClient = httpClient,
            json = json,
            repoEndpointUrl = mockServer.url("/").toString(),
            scope = scope,
            nodeEventManager = mockEventManager,
            onMarkAcknowledgedAndGetNextOutgoingReplications = mock { },
            onStartPendingSession = mock { },
            onPendingSessionResolved = mock { },
            logger = mock  { },
            dbName = "testdb",
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
        val ackAndGetPendingRequestCount = AtomicInteger()
        val ackRequests = MutableSharedFlow<RecordedRequest>(replay = 10)
        mockServer.dispatcher = object: Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return when(request.path) {
                    "/replication/nodeId" -> {
                        MockResponse().setHeader(DoorConstants.HEADER_NODE_ID, serverNodeId.toString())
                            .setResponseCode(204)
                    }

                    "/replication/ackAndGetPendingReplications" -> {
                        val requestCount = ackAndGetPendingRequestCount.incrementAndGet()
                        ackRequests.tryEmit(request)
                        if(requestCount == 2) {
                            MockResponse()
                                .setHeader("content-type", "application/json")
                                .setBody(json.encodeToString(DoorMessage.serializer(), pendingReplicationMessage))
                        }else {
                            MockResponse().setResponseCode(204)
                        }
                    }

                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val mockEventManager = spy(TestEventManager(scope))
        val repoClient = DoorRepositoryReplicationClient(
            localNodeId = clientNodeId,
            localNodeAuth = "secret",
            httpClient = httpClient,
            json = json,
            repoEndpointUrl = mockServer.url("/").toString(),
            scope = scope,
            nodeEventManager = mockEventManager,
            onMarkAcknowledgedAndGetNextOutgoingReplications = mock {
               onBlocking {
                   invoke(any(),any(), any())
               }.thenReturn(emptyList())
            },
            onStartPendingSession = mock { },
            onPendingSessionResolved = mock { },
            logger = NapierDoorLogger(),
            dbName = "testdb",
        )

        //wait for the first request when it has initialized
        runBlocking {
            ackRequests.filter { it.path == "/replication/ackAndGetPendingReplications" }.first()

            //This shouldn't really be needed.
            delay(100)
        }


        runBlocking {
            mockEventManager.onIncomingMessageReceived(
                DoorMessage(
                    what = DoorMessage.WHAT_REPLICATION_PUSH,
                    fromNode = serverNodeId,
                    toNode = clientNodeId,
                    replications = emptyList()
                )
            )
        }

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

    private inner class MockMessageReceiveDispatcher: Dispatcher() {

        val doorMessagesReceived = MutableSharedFlow<DoorMessage>(replay = 20)
        override fun dispatch(request: RecordedRequest): MockResponse {

            return when(request.path) {
                "/replication/nodeId" -> MockResponse()
                    .setHeader(DoorConstants.HEADER_NODE_ID, serverNodeId)
                "/replication/message" -> {
                    val bodyStr = request.body.readString(Charsets.UTF_8)
                    val message = json.decodeFromString<DoorMessage>(bodyStr)
                    doorMessagesReceived.tryEmit(message)

                    MockResponse()
                        .setHeader("content-type", "application/json")
                        .setBody(
                            json.encodeToString(
                                ReplicationReceivedAck.serializer(),
                                ReplicationReceivedAck(message.replications.map { it.orUid })
                            )
                        )
                }
                else -> MockResponse().setResponseCode(404)
            }
        }
    }

    @Test
    fun givenOutgoingEntitiesPending_whenClientReceivesNodeEvent_thenShouldSendPendingOutgoingEntities() {
        val allAckedEntities =  MutableSharedFlow<List<Long>>(replay = 10)

        val messageReceiveDispatcher = MockMessageReceiveDispatcher()
        mockServer.dispatcher = messageReceiveDispatcher

        val mockEventManager = spy<NodeEventManager<*>>(TestEventManager(scope))
        val pendingEntities = buildList {
            addAll(pendingReplicationMessage.replications)
        }
        val ackedReplications = mutableListOf<Long>()

        val onMarkAcknowledgedAndGetNextOutgoingReplications: DoorRepositoryReplicationClient.OnMarkAcknowledgedAndGetNextOutgoingReplications = mock {
            onBlocking {
                invoke(eq(serverNodeId), any(), any())
            }.thenAnswer { invocation ->
                val ackedEntities = invocation.arguments[1] as ReplicationReceivedAck
                ackedReplications.addAll(ackedEntities.replicationUids)
                allAckedEntities.tryEmit(ackedReplications.toList())
                pendingEntities.filter { it.orUid !in ackedReplications }
            }
        }

        val repoClient = DoorRepositoryReplicationClient(
            localNodeId = clientNodeId,
            localNodeAuth = "secret",
            httpClient = httpClient,
            json = json,
            repoEndpointUrl = mockServer.url("/").toString(),
            scope = scope,
            nodeEventManager = mockEventManager,
            onMarkAcknowledgedAndGetNextOutgoingReplications = onMarkAcknowledgedAndGetNextOutgoingReplications,
            onStartPendingSession = mock { },
            onPendingSessionResolved = mock { },
            logger = mock  { },
            dbName = "testdb",
        )

        runBlocking {
            messageReceiveDispatcher.doorMessagesReceived.filter { message ->
                message.replications == pendingReplicationMessage.replications
            }.test(timeout = 5.seconds, name = "pending replications were sent to remote message endpoint") {
                assertNotNull(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }

            allAckedEntities.filter {  ackedList ->
                ackedList.containsAll(pendingEntities.map { it.orUid })
            }.test(timeout = 5.seconds) {
                assertNotNull(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

        repoClient.close()
    }

    @Test
    fun givenNoOutgoingEntitiesPending_whenNewEventForServerNodeEmitted_thenShouldSendReplications() {
        val mockEventManager = spy(TestEventManager(scope))
        val pendingEntities = mutableListOf<DoorReplicationEntity>()
        val ackedReplications = mutableListOf<Long>()
        val allAckedEntities =  MutableSharedFlow<List<Long>>(replay = 10)


        val messageReceiveDispatcher = MockMessageReceiveDispatcher()
        mockServer.dispatcher = messageReceiveDispatcher

        val onMarkAcknowledgedAndGetNextOutgoingReplications: DoorRepositoryReplicationClient.OnMarkAcknowledgedAndGetNextOutgoingReplications = mock {
            onBlocking {
                invoke(eq(serverNodeId), any(), any())
            }.thenAnswer { invocation ->
                val ackedEntities = invocation.arguments[1] as ReplicationReceivedAck
                ackedReplications.addAll(ackedEntities.replicationUids)
                allAckedEntities.tryEmit(ackedReplications.toList())
                pendingEntities.filter { it.orUid !in ackedReplications }
            }
        }

        val repoClient = DoorRepositoryReplicationClient(
            localNodeId = clientNodeId,
            localNodeAuth = "secret",
            httpClient = httpClient,
            json = json,
            repoEndpointUrl = mockServer.url("/").toString(),
            scope = scope,
            nodeEventManager = mockEventManager,
            onMarkAcknowledgedAndGetNextOutgoingReplications = onMarkAcknowledgedAndGetNextOutgoingReplications,
            onStartPendingSession = mock { },
            onPendingSessionResolved = mock { },
            logger = mock  { },
            dbName = "testdb",
        )

        //Wait for the first check for pending outgoing replications
        runBlocking { allAckedEntities.first() }

        //Add a pending outgoing replication
        pendingEntities.addAll(pendingReplicationMessage.replications)
        mockEventManager.emitOutgoingEvents(listOf(
            NodeEvent(
                what = DoorMessage.WHAT_REPLICATION_PUSH,
                toNode = serverNodeId,
                tableId = 1,
                key1 = 0,
                key2 = 0,
            )
        ))

        runBlocking {
            messageReceiveDispatcher.doorMessagesReceived.filter {
                it.replications.containsAll(pendingReplicationMessage.replications)
            }.test(timeout = 5.seconds, name = "When outgoing event is emitted then server receives pending replication") {
                assertNotNull(awaitItem())
            }

            allAckedEntities.filter { ackedList ->
                ackedList.containsAll(pendingReplicationMessage.replications.map { it.orUid })
            }.test(timeout = 5.seconds, name = "When outgoing event is emitted then client will call onMarkAcknowledged") {
                assertNotNull(awaitItem())
            }
        }

        repoClient.close()
    }


}