package com.ustadmobile.lib.annotationprocessor.core.replication

import com.ustadmobile.door.*
import com.ustadmobile.door.entities.DoorNode
import com.ustadmobile.door.entities.NodeIdAndAuth
import com.ustadmobile.door.ext.DoorTag
import com.ustadmobile.door.ext.asRepository
import com.ustadmobile.door.ext.doorDatabaseMetadata
import com.ustadmobile.door.ext.waitUntilWithTimeout
import com.ustadmobile.door.replication.*
import com.ustadmobile.door.util.NodeIdAuthCache
import com.ustadmobile.lib.annotationprocessor.core.VirtualHostScope
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import io.ktor.application.*
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.features.json.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.utils.io.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.kodein.di.*
import org.kodein.di.ktor.DIFeature
import org.kodein.type.erased
import repdb.RepDb
import repdb.RepEntity
import repdb.RepDb_ReplicationRunOnChangeRunner

class TestDoorDatabaseRepositoryReplicationExt  {

    private lateinit var remoteServer: ApplicationEngine

    private lateinit var remoteDi: DI

    private lateinit var remoteRepDb: RepDb

    private lateinit var localRepDb: RepDb

    private lateinit var localDbRepo: RepDb

    private lateinit var remoteVirtualHostScope: VirtualHostScope

    private lateinit var httpClient: HttpClient

    private lateinit var okHttpClient: OkHttpClient

    private lateinit var jsonSerializer: Json

    private lateinit var remoteNotificationDispatcher :ReplicationNotificationDispatcher

    private lateinit var localNotificationDispatcher: ReplicationNotificationDispatcher

    private lateinit var localReplicationSubscriptionManager: ReplicationSubscriptionManager

    @Before
    fun setup() {
        Napier.takeLogarithm()
        Napier.base(DebugAntilog())

        jsonSerializer = Json {
            encodeDefaults = true
        }

        okHttpClient = OkHttpClient.Builder().build()

        httpClient = HttpClient(OkHttp) {
            install(JsonFeature)
            engine {
                preconfigured = okHttpClient
            }
        }

        remoteRepDb = DatabaseBuilder.databaseBuilder(Any(), RepDb::class, "RepDbRemote")
            .build().also {
                it.clearAllTables()
            }

        localRepDb = DatabaseBuilder.databaseBuilder(Any(), RepDb::class, "RepDbLocal")
            .build().also {
                it.clearAllTables()
            }

        localDbRepo = localRepDb.asRepository(RepositoryConfig.repositoryConfig(Any(), "http://localhost:8089/",
            LOCAL_NODE_ID, "secret", httpClient, okHttpClient))


        remoteVirtualHostScope = VirtualHostScope()

        remoteNotificationDispatcher = ReplicationNotificationDispatcher(
            remoteRepDb, RepDb_ReplicationRunOnChangeRunner(remoteRepDb), GlobalScope,
            RepDb::class.doorDatabaseMetadata())

        localNotificationDispatcher = ReplicationNotificationDispatcher(
            localRepDb, RepDb_ReplicationRunOnChangeRunner(localRepDb), GlobalScope,
            RepDb::class.doorDatabaseMetadata())


        remoteDi = DI {
            bind<RepDb>(tag = DoorTag.TAG_DB) with scoped(remoteVirtualHostScope).singleton {
                remoteRepDb
            }

            bind<ReplicationNotificationDispatcher>() with scoped(remoteVirtualHostScope).singleton {
                remoteNotificationDispatcher
            }

            bind<NodeIdAndAuth>() with scoped(remoteVirtualHostScope).singleton {
                NodeIdAndAuth(REMOTE_NODE_ID, "secret")
            }

            bind<NodeIdAuthCache>() with scoped(remoteVirtualHostScope).singleton {
                NodeIdAuthCache(instance<RepDb>(tag = DoorTag.TAG_DB))
            }

            registerContextTranslator { _: ApplicationCall -> "localhost" }
        }

        remoteServer = embeddedServer(Netty, 8089, configure = {
            requestReadTimeoutSeconds = 600
            responseWriteTimeoutSeconds = 600
        }) {
            install(DIFeature){
                extend(remoteDi)
            }

            routing {
                doorReplicationRoute(erased(), RepDb::class, jsonSerializer)
            }
        }
        remoteServer.start()


    }


    private fun setupLocalAndRemoteReplicationManager() {
        remoteRepDb.addChangeListener(ChangeListenerRequest(listOf(), remoteNotificationDispatcher))
        localRepDb.addChangeListener(ChangeListenerRequest(listOf(), localNotificationDispatcher))

        localReplicationSubscriptionManager = ReplicationSubscriptionManager(localRepDb.dbVersion, jsonSerializer, localNotificationDispatcher,
            localDbRepo as DoorDatabaseRepository, GlobalScope, RepDb::class.doorDatabaseMetadata(), RepDb::class)
    }


    @After
    fun tearDown() {
        if(::localReplicationSubscriptionManager.isInitialized)
            localReplicationSubscriptionManager.close()
        remoteServer.stop(1000, 1000)
    }

    /**
     *
     */
    @Test
    fun givenEntityCreatedLocally_whenSendPendingReplicationsCalled_thenShouldBePresenterOnRemote() {
        localRepDb.repDao.insertDoorNode(DoorNode().apply {
            auth = "secret"
            nodeId = REMOTE_NODE_ID
        })

        val repEntity = RepEntity().apply {
            reString = "Hello World"
            rePrimaryKey = localRepDb.repDao.insert(this)
        }


        runBlocking { localRepDb.repDao.updateReplicationTrackers(0) }

        runBlocking {
            (localDbRepo as DoorDatabaseRepository).sendPendingReplications(jsonSerializer, RepEntity.TABLE_ID,
                REMOTE_NODE_ID)
        }

        Assert.assertNotNull("entity now on remote", remoteRepDb.repDao.findByUid(repEntity.rePrimaryKey))
    }

    @Test
    fun givenEntityCreatedRemotely_whenFetchPendingReplicationsCalled_thenShouldBePresentOnLocal() {
        remoteRepDb.repDao.insertDoorNode(DoorNode().apply {
            auth = "secret"
            nodeId = LOCAL_NODE_ID
        })

        val repEntity = RepEntity().apply {
            reString = "Fetch"
            rePrimaryKey = remoteRepDb.repDao.insert(this)
        }

        runBlocking { remoteRepDb.repDao.updateReplicationTrackers(0) }

        runBlocking {
            (localDbRepo as DoorDatabaseRepository).fetchPendingReplications(jsonSerializer, RepEntity.TABLE_ID,
                REMOTE_NODE_ID)
        }

        Assert.assertNotNull("Entity now on local", localRepDb.repDao.findByUid(repEntity.rePrimaryKey))
    }

    @Test
    fun givenMoreEntitiesThanInBatchCreatedRemotely_whenFetchPendingReplicationsCalled_thenShouldAllBePresentOnLocal() {
        remoteRepDb.repDao.insertDoorNode(DoorNode().apply {
            auth = "secret"
            nodeId = LOCAL_NODE_ID
        })

        remoteRepDb.repDao.insertList((0..1500).map {
            RepEntity().apply {
                reString = "Fetch $it"
                reNumField = it
            }
        })

        runBlocking { remoteRepDb.repDao.updateReplicationTrackers(0) }

        runBlocking {
            (localDbRepo as DoorDatabaseRepository).fetchPendingReplications(jsonSerializer, RepEntity.TABLE_ID,
                REMOTE_NODE_ID)
        }

        Assert.assertEquals("All entities transferred", remoteRepDb.repDao.countEntities(),
            localRepDb.repDao.countEntities())
    }


    @Test
    fun givenMoreEntitiesThanInBatchCreatedLocally_whenSendPendingReplicationsCalled_thenAllShouldBePresentOnRemote() {
        localRepDb.repDao.insertDoorNode(DoorNode().apply {
            auth = "secret"
            nodeId = REMOTE_NODE_ID
        })

        localRepDb.repDao.insertList((0..1500).map {
            RepEntity().apply {
                reString = "Hello World $it"
                reNumField = it
            }
        })

        runBlocking { localRepDb.repDao.updateReplicationTrackers(0) }

        runBlocking {
            (localDbRepo as DoorDatabaseRepository).sendPendingReplications(jsonSerializer, RepEntity.TABLE_ID,
                REMOTE_NODE_ID)
        }

        Assert.assertEquals("All entities transferred", localRepDb.repDao.countEntities(),
            remoteRepDb.repDao.countEntities())
    }


    @Test
    fun givenReplicationSubscriptionEnabled_whenChangeMadeOnRemote_thenShouldTransferToLocal() {
        setupLocalAndRemoteReplicationManager()

        //Just create it - this will need a close
        val entity = RepEntity().apply {
            reString = "Subscribe and replicate"
            rePrimaryKey = remoteRepDb.repDao.insert(this)
        }

        val entityOnLocal = runBlocking {
            localRepDb.repDao.findByUidLive(entity.rePrimaryKey).waitUntilWithTimeout(5000) {
                it != null
            }
        }

        Assert.assertNotNull(entityOnLocal)
    }

    @Test
    fun givenReplicationSubscriptionEnabled_whenInsertedOnLocal_thenShouldTransferToRemote() {
        setupLocalAndRemoteReplicationManager()

        //Just create it - this will need a close
        val entity = RepEntity().apply {
            reString = "Subscribe and replicate"
            rePrimaryKey = localRepDb.repDao.insert(this)
        }

        val entityOnRemote = runBlocking {
            remoteRepDb.repDao.findByUidLive(entity.rePrimaryKey).waitUntilWithTimeout(5000) {
                it != null
            }
        }

        Assert.assertNotNull(entityOnRemote)
    }

    @Test
    fun givenReplicationSubscriptionEnabled_whenChangeMadeOnRemoteThenLocal_thenShouldTransferToLocalAndUpdateOnRemote() {
        setupLocalAndRemoteReplicationManager()

        //Just create it - this will need a close
        val entity = RepEntity().apply {
            reString = "Subscribe and replicate"
            rePrimaryKey = remoteRepDb.repDao.insert(this)
        }

        val entityOnLocal = runBlocking {
            localRepDb.repDao.findByUidLive(entity.rePrimaryKey).waitUntilWithTimeout(5000 * 1000) {
                it != null
            }
        } ?: throw IllegalStateException("Entity not transferred to local")

        entityOnLocal.reString = "Updated"

        localRepDb.repDao.update(entityOnLocal)

        val entityUpdatedOnRemote = runBlocking {
            remoteRepDb.repDao.findByUidLive(entity.rePrimaryKey).waitUntilWithTimeout(5000) {
                (it?.reLastChangeTime ?: 0) == entityOnLocal.reLastChangeTime
            }
        }

        Assert.assertEquals("Got updated entity back on remote", entityOnLocal.reLastChangeTime,
            entityUpdatedOnRemote?.reLastChangeTime)

        Assert.assertNotNull(entityOnLocal)
    }

    @Test
    fun givenReplicationSubscriptionEnabled_whenChangeMadeOnLocalThenRemote_thenShouldTransferToRemoteAndUpdateOnLocal() {
        setupLocalAndRemoteReplicationManager()

        //Just create it - this will need a close
        val entity = RepEntity().apply {
            reString = "Subscribe and replicate"
            rePrimaryKey = localRepDb.repDao.insert(this)
        }

        val entityOnRemote = runBlocking {
            remoteRepDb.repDao.findByUidLive(entity.rePrimaryKey).waitUntilWithTimeout(5000) {
                it != null
            }
        } ?: throw IllegalStateException("Entity not transferred to local")

        entityOnRemote.reString = "Updated"

        remoteRepDb.repDao.update(entityOnRemote)

        val entityUpdatedOnLocal = runBlocking {
            localRepDb.repDao.findByUidLive(entity.rePrimaryKey).waitUntilWithTimeout(5000) {
                (it?.reLastChangeTime ?: 0) == entityOnRemote.reLastChangeTime
            }
        }

        Assert.assertEquals("Got updated entity back on remote", entityOnRemote.reLastChangeTime,
            entityUpdatedOnLocal?.reLastChangeTime)

        Assert.assertNotNull(entityOnRemote)
    }


    companion object {

        private const val REMOTE_NODE_ID = 1234L

        private const val LOCAL_NODE_ID = 1330L
    }

}