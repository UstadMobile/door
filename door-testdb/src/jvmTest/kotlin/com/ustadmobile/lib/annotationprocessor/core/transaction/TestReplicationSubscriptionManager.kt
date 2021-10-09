package com.ustadmobile.lib.annotationprocessor.core.transaction

import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.door.DoorDatabaseRepository
import com.ustadmobile.door.RepositoryConfig
import com.ustadmobile.door.asRepository
import com.ustadmobile.door.ext.doorDatabaseMetadata
import com.ustadmobile.door.replication.ReplicationNotificationDispatcher
import com.ustadmobile.door.replication.ReplicationPendingEvent
import com.ustadmobile.door.replication.ReplicationPendingListener
import com.ustadmobile.door.replication.ReplicationSubscriptionManager
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


class TestReplicationSubscriptionManager {

    private lateinit var db: RepDb

    private lateinit var repo: RepDb

    private lateinit var httpClient: HttpClient

    private lateinit var okHttpClient: OkHttpClient

    private lateinit var json: Json

    private val testRemoteNodeId = 43232L

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
            1234, "", httpClient, okHttpClient))

    }

    @Test
    fun givenSubscriptionInitialized_whenRemoteInvalidateMessageReceived_thenShouldCallFetchReplications() {
        val dispatcher = mock<ReplicationNotificationDispatcher> { }
        val mockEventSource = mock<DoorEventSource> { }
        val mockEventSourceFactory = mock<DoorEventSourceFactory> {
            on { makeNewDoorEventSource(any(), any(), any()) }.thenReturn(mockEventSource)
        }

        val mockSendReplications = mock<ReplicationSubscriptionManager.ReplicateRunner> { }
        val mockFetchReplications = mock<ReplicationSubscriptionManager.ReplicateRunner> { }

        val subscriptionManager = ReplicationSubscriptionManager(json, dispatcher, repo as DoorDatabaseRepository,
            GlobalScope, RepDb::class.doorDatabaseMetadata(), RepDb::class, 5,mockEventSourceFactory,
            mockSendReplications, mockFetchReplications)

        subscriptionManager.onMessage(DoorServerSentEvent("1", "INIT", "$testRemoteNodeId"))

        //Make sure that the first fetch replications runs
        verifyBlocking(mockFetchReplications, timeout(5000).times(1)) {
            replicate(repo as DoorDatabaseRepository, RepEntity.TABLE_ID, testRemoteNodeId)
        }

        subscriptionManager.onMessage(DoorServerSentEvent("2", "INVALIDATE", RepEntity.TABLE_ID.toString()))

        verifyBlocking(mockFetchReplications, timeout(5000).times(2)) {
            replicate(repo as DoorDatabaseRepository, RepEntity.TABLE_ID, testRemoteNodeId)
        }
    }

    @Suppress("RedundantUnitExpression")//When it's removed it won't compile
    @Test
    fun givenSubscriptionInitialized_whenLocalInvalidateReceived_thenShouldCallSendReplications() {
        var remoteListener: ReplicationPendingListener? = null
        val dispatcher = mock<ReplicationNotificationDispatcher> {
            onBlocking { addReplicationPendingEventListener(eq(testRemoteNodeId), any()) }.thenAnswer {
                remoteListener = (it.arguments[1] as ReplicationPendingListener)
                Unit
            }
        }

        val mockEventSource = mock<DoorEventSource> { }
        val mockEventSourceFactory = mock<DoorEventSourceFactory> {
            on { makeNewDoorEventSource(any(), any(), any()) }.thenAnswer {
                mockEventSource
            }
        }

        val mockSendReplications = mock<ReplicationSubscriptionManager.ReplicateRunner> { }
        val mockFetchReplications = mock<ReplicationSubscriptionManager.ReplicateRunner> { }

        val subscriptionManager = ReplicationSubscriptionManager(json, dispatcher, repo as DoorDatabaseRepository,
            GlobalScope, RepDb::class.doorDatabaseMetadata(), RepDb::class, 5,mockEventSourceFactory,
            mockSendReplications, mockFetchReplications)


        subscriptionManager.onMessage(DoorServerSentEvent("1", "INIT", "$testRemoteNodeId"))

        verifyBlocking(mockSendReplications, timeout(5000)) {
            replicate(any(), eq(RepEntity.TABLE_ID), eq(testRemoteNodeId))
        }
        remoteListener!!.onReplicationPending(ReplicationPendingEvent(0L, listOf(RepEntity.TABLE_ID)))

        verifyBlocking(mockSendReplications, timeout(5000).times(2)) {
            replicate(any(), eq(RepEntity.TABLE_ID), eq(testRemoteNodeId))
        }
    }



}