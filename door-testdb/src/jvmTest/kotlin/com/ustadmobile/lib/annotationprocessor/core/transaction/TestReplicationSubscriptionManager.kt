package com.ustadmobile.lib.annotationprocessor.core.transaction

import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.door.DoorDatabaseRepository
import com.ustadmobile.door.RepositoryConfig
import com.ustadmobile.door.asRepository
import com.ustadmobile.door.ext.doorDatabaseMetadata
import com.ustadmobile.door.replication.ReplicationNotificationDispatcher
import com.ustadmobile.door.replication.ReplicationSubscriptionManager
import com.ustadmobile.door.sse.DoorEventSource
import com.ustadmobile.door.sse.DoorEventSourceFactory
import com.ustadmobile.door.sse.DoorServerSentEvent
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.features.json.*
import kotlinx.coroutines.GlobalScope
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

    @Before
    fun setup() {
        db = DatabaseBuilder.databaseBuilder(Any(), RepDb::class, "RepDb").build()
            .apply {
                clearAllTables()
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
    fun givenMessageThenShouldCallReplicate() {
        val dispatcher = mock<ReplicationNotificationDispatcher> { }
        val mockEventSource = mock<DoorEventSource> { }
        val mockEventSourceFactory = mock<DoorEventSourceFactory> {
            on { makeNewDoorEventSource(any(), any(), any()) }.thenReturn(mockEventSource)
        }

        val mockSendReplications = mock<ReplicationSubscriptionManager.ReplicateRunner> { }
        val mockFetchReplications = mock<ReplicationSubscriptionManager.ReplicateRunner> { }

        val subscriptionManager = ReplicationSubscriptionManager(dispatcher, repo as DoorDatabaseRepository,
            GlobalScope, RepDb::class.doorDatabaseMetadata(), RepDb::class, 5,mockEventSourceFactory,
            mockSendReplications, mockFetchReplications)

        subscriptionManager.onMessage(DoorServerSentEvent("1", "INIT", "0"))

        verifyBlocking(mockSendReplications, timeout(5000 * 1000)) {
            replicate(repo as DoorDatabaseRepository, RepEntity.TABLE_ID)
        }
    }

}