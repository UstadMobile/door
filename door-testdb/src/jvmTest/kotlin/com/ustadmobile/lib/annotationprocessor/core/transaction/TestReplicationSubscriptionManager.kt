package com.ustadmobile.lib.annotationprocessor.core.transaction

import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.door.DoorDatabaseRepository
import com.ustadmobile.door.RepositoryConfig
import com.ustadmobile.door.ext.asRepository
import com.ustadmobile.door.ext.doorDatabaseMetadata
import com.ustadmobile.door.replication.*
import com.ustadmobile.door.sse.DoorEventSource
import com.ustadmobile.door.sse.DoorEventSourceFactory
import com.ustadmobile.door.sse.DoorServerSentEvent
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.features.json.*
import kotlinx.coroutines.GlobalScope
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Before
import org.junit.Test
import repdb.RepDb
import org.mockito.kotlin.*
import repdb.RepEntity
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit


class TestReplicationSubscriptionManager {

    private lateinit var db: RepDb

    private lateinit var repo: RepDb

    private lateinit var httpClient: HttpClient

    private lateinit var okHttpClient: OkHttpClient

    private lateinit var json: Json

    private val testRemoteNodeId = 43232L

    private lateinit var mockEventSource: DoorEventSource

    private lateinit var mockEventSourceFactory: DoorEventSourceFactory

    private lateinit var mockSendReplications: ReplicationSubscriptionManager.ReplicateRunner

    private lateinit var mockFetchReplications: ReplicationSubscriptionManager.ReplicateRunner

    @Before
    fun setup() {
        db = DatabaseBuilder.databaseBuilder(Any(), RepDb::class, "RepDb").build()
            .apply {
                clearAllTables()
            }

        json = Json {
            encodeDefaults = true
        }

        okHttpClient = OkHttpClient.Builder().build()

        httpClient = HttpClient(OkHttp) {
            install(JsonFeature)
            engine {
                preconfigured = okHttpClient
            }
        }

        repo = db.asRepository(RepositoryConfig.repositoryConfig(Any(), "http://localhost/dummy",
            1234, "", httpClient, okHttpClient, Json { encodeDefaults = true }) {
            this.useReplicationSubscription =  false
            this.replicationSubscriptionMode = ReplicationSubscriptionMode.MANUAL
        })

        mockEventSource = mock<DoorEventSource> { }
        mockEventSourceFactory = mock<DoorEventSourceFactory> {
            on { makeNewDoorEventSource(any(), any(), any()) }.thenReturn(mockEventSource)
        }

        mockSendReplications = mock<ReplicationSubscriptionManager.ReplicateRunner> { }
        mockFetchReplications = mock<ReplicationSubscriptionManager.ReplicateRunner> { }

    }

    @Test
    fun givenSubscriptionInitialized_whenRemoteInvalidateMessageReceived_thenShouldCallInitListenerAndFetchReplications() {
        val dispatcher = mock<ReplicationNotificationDispatcher> { }

        val mockInitListener = mock<ReplicationSubscriptionManager.SubscriptionInitializedListener> { }

        val subscriptionManager = ReplicationSubscriptionManager(1, json, dispatcher,
            repo as DoorDatabaseRepository, GlobalScope, RepDb::class.doorDatabaseMetadata(), RepDb::class,
            5, mockEventSourceFactory, mockSendReplications, mockFetchReplications, mockInitListener)
        subscriptionManager.enabled = true

        subscriptionManager.onMessage(DoorServerSentEvent("1", "INIT", "$testRemoteNodeId"))

        subscriptionManager.onMessage(DoorServerSentEvent("2", "INVALIDATE", RepEntity.TABLE_ID.toString()))

        verifyBlocking(mockFetchReplications, timeout(5000)) {
            replicate(repo as DoorDatabaseRepository, RepEntity.TABLE_ID, testRemoteNodeId)
        }
        verifyBlocking(mockInitListener) {
            onSubscriptionInitialized(any(), eq(testRemoteNodeId))
        }
        verify(dispatcher).onNewDoorNode(eq(testRemoteNodeId), any())
    }

    @Suppress("RedundantUnitExpression")//When it's removed it won't compile
    @Test
    fun givenSubscriptionInitialized_whenLocalInvalidateReceived_thenShouldCallSendReplications() {
        var remoteListener: ReplicationPendingListener? = null
        val addReplicationListenerLatch = CountDownLatch(1)
        val dispatcher = mock<ReplicationNotificationDispatcher> {
            onBlocking { addReplicationPendingEventListener(eq(testRemoteNodeId), any()) }.thenAnswer {
                remoteListener = (it.arguments[1] as ReplicationPendingListener)
                addReplicationListenerLatch.countDown()
                Unit
            }
        }


        val subscriptionManager = ReplicationSubscriptionManager(1, json, dispatcher,
            repo as DoorDatabaseRepository, GlobalScope, RepDb::class.doorDatabaseMetadata(), RepDb::class,
            5, mockEventSourceFactory, mockSendReplications, mockFetchReplications)
        subscriptionManager.enabled = true


        subscriptionManager.onMessage(DoorServerSentEvent("1", "INIT", "$testRemoteNodeId"))

        addReplicationListenerLatch.await(5, TimeUnit.SECONDS)
        remoteListener!!.onReplicationPending(ReplicationPendingEvent(0L, listOf(RepEntity.TABLE_ID)))

        verifyBlocking(mockSendReplications, timeout(5000).times(1)) {
            replicate(any(), eq(RepEntity.TABLE_ID), eq(testRemoteNodeId))
        }
    }


    @Test
    fun givenSubscriptionManagerStartedInManualMode_whenDisabled_thenShouldNotCallSendReplications() {
        val dispatcher = mock<ReplicationNotificationDispatcher> { }

        val subscriptionManager = ReplicationSubscriptionManager(1, json, dispatcher,
            repo as DoorDatabaseRepository, GlobalScope, RepDb::class.doorDatabaseMetadata(), RepDb::class,
            5, mockEventSourceFactory, mockSendReplications, mockFetchReplications)

        subscriptionManager.onMessage(DoorServerSentEvent("1", "INIT", "$testRemoteNodeId"))

        subscriptionManager.onMessage(DoorServerSentEvent("2", "INVALIDATE", RepEntity.TABLE_ID.toString()))

        Thread.sleep(2000)

        verifyNoInteractions(mockSendReplications)
        verifyNoInteractions(mockFetchReplications)
        verifyNoInteractions(mockEventSourceFactory)
    }



}