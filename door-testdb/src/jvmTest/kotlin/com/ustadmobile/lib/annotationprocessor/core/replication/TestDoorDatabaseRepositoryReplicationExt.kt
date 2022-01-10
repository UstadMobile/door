package com.ustadmobile.lib.annotationprocessor.core.replication

import com.ustadmobile.door.*
import com.ustadmobile.door.entities.DoorNode
import com.ustadmobile.door.entities.NodeIdAndAuth
import com.ustadmobile.door.ext.*
import com.ustadmobile.door.replication.*
import com.ustadmobile.door.util.NodeIdAuthCache
import com.ustadmobile.door.util.systemTimeInMillis
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
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.*
import org.kodein.di.*
import org.kodein.di.ktor.DIFeature
import org.kodein.type.erased
import repdb.RepDb
import repdb.RepEntity

class TestDoorDatabaseRepositoryReplicationExt  {

    private lateinit var remoteServer: ApplicationEngine

    private lateinit var remoteDi: DI

    private lateinit var remoteRepDb: RepDb

    private lateinit var localRepDb: RepDb

    private lateinit var localRepDbRepo: RepDb

    private lateinit var localRepDb2: RepDb

    private lateinit var localRepDb2Repo: RepDb

    private lateinit var remoteVirtualHostScope: VirtualHostScope

    private lateinit var httpClient: HttpClient

    private lateinit var okHttpClient: OkHttpClient

    private lateinit var jsonSerializer: Json

    @Before
    fun setup() {
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

        remoteVirtualHostScope = VirtualHostScope()

        remoteDi = DI {
            bind<RepDb>(tag = DoorTag.TAG_DB) with scoped(remoteVirtualHostScope).singleton {
                remoteRepDb
            }

            bind<NodeIdAndAuth>() with scoped(remoteVirtualHostScope).singleton {
                NodeIdAndAuth(REMOTE_NODE_ID, "secret")
            }

            bind<NodeIdAuthCache>() with scoped(remoteVirtualHostScope).singleton {
                instance<RepDb>(tag = DoorTag.TAG_DB).nodeIdAuthCache
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

        localRepDbRepo = localRepDb.asRepository(RepositoryConfig.repositoryConfig(Any(), "http://localhost:8089/",
            LOCAL_NODE_ID, "secret", httpClient, okHttpClient, jsonSerializer
        ) {
            useReplicationSubscription = true
        })
    }

    //Setup a second local copy (e.g. simulate a second client)
    private fun setupLocalDb2() {
        localRepDb2 = DatabaseBuilder.databaseBuilder(Any(), RepDb::class, "RepDbLocal2")
            .build().also {
                it.clearAllTables()
            }

        localRepDb2Repo = localRepDb2.asRepository(RepositoryConfig.repositoryConfig(Any(), "http://localhost:8089/",
            LOCAL_NODE_ID2, "secret", httpClient, okHttpClient, jsonSerializer
        ) {
            useReplicationSubscription = true
        })

    }


    @After
    fun tearDown() {
        remoteServer.stop(1000, 1000)
    }

    /**
     *
     */
    @Test
    fun givenEmptyDatabase_whenEntityCreatedLocally_thenShouldReplicateToRemote() {
        localRepDb.repDao.insertDoorNode(DoorNode().apply {
            auth = "secret"
            nodeId = REMOTE_NODE_ID
        })

        val repEntity = RepEntity().apply {
            reString = "Hello World"
            rePrimaryKey = localRepDb.repDao.insert(this)
        }

        runBlocking {
            remoteRepDb.repDao.findByUidLive(repEntity.rePrimaryKey).waitUntilWithTimeout(5000) {
                it != null
            }
        }

        Assert.assertNotNull("entity now on remote", remoteRepDb.repDao.findByUid(repEntity.rePrimaryKey))
    }

    @Test
    fun givenEmptyDatabase_whenEntityCreatedRemotely_thenShouldBeReplicatedToLocal() {
        val repEntity = RepEntity().apply {
            reString = "Fetch"
            rePrimaryKey = remoteRepDb.repDao.insert(this)
        }

        runBlocking {
            localRepDb.repDao.findByUidLive(repEntity.rePrimaryKey).waitUntilWithTimeout(5000) {
                it != null
            }
        }

        Assert.assertNotNull("Entity now on local", localRepDb.repDao.findByUid(repEntity.rePrimaryKey))
    }

    @Test
    fun givenEmptyDatabase_whenEntityWithNullStringCreatedRemotely_thenShouldReplicateAndBeEqual() {
        val repEntity = RepEntity().apply {
            reString = null
            rePrimaryKey = remoteRepDb.repDao.insert(this)
        }

        runBlocking {
            localRepDb.repDao.findByUidLive(repEntity.rePrimaryKey).waitUntilWithTimeout(5000) {
                it != null
            }
        }

        val entityOnLocal = localRepDb.repDao.findByUid(repEntity.rePrimaryKey)
        Assert.assertNotNull("Entity now on local", entityOnLocal)
        Assert.assertEquals("Entity on local is the same as the one on remote", repEntity, entityOnLocal)
    }

    @Test
    fun givenEmptyDatabase_whenEntityWithArabicCharsCreated_thenShouldReplicateAndBeEqual() {
        val repEntity = RepEntity().apply {
            reString = "سلام"
            rePrimaryKey = remoteRepDb.repDao.insert(this)
        }

        runBlocking {
            localRepDb.repDao.findByUidLive(repEntity.rePrimaryKey).waitUntilWithTimeout(5000 * 1000) {
                it != null
            }
        }

        val entityOnLocal = localRepDb.repDao.findByUid(repEntity.rePrimaryKey)
        Assert.assertNotNull("Entity now on local", entityOnLocal)
        Assert.assertEquals("Entity on local is the same as the one on remote", repEntity, entityOnLocal)
    }


    @Test
    fun givenEmptyDatabase_whenMoreEntitiesThanInBatchCreatedRemotely_thenShouldAllReplicateToLocal() {
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

        runBlocking {
            localRepDb.repDao.countEntitiesLive().waitUntilWithTimeout(5000) {
                it == remoteRepDb.repDao.countEntities()
            }
        }


        Assert.assertEquals("All entities transferred", remoteRepDb.repDao.countEntities(),
            localRepDb.repDao.countEntities())
    }


    @Test
    fun givenEmptyDatabase_whenMoreEntitiesThanInBatchCreatedLocally_thenAllShouldBeReplicatedToRemote() {
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

        runBlocking {
            remoteRepDb.repDao.countEntitiesLive().waitUntilWithTimeout(5000) {
                it == localRepDb.repDao.countEntities()
            }
        }

        Assert.assertEquals("All entities transferred", localRepDb.repDao.countEntities(),
            remoteRepDb.repDao.countEntities())
    }


    @Test
    fun givenReplicationSubscriptionEnabled_whenChangeMadeOnRemote_thenShouldTransferToLocal() {
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
        //Just create it - this will need a close
        val entity = RepEntity().apply {
            reString = "Subscribe and replicate"
            rePrimaryKey = remoteRepDb.repDao.insert(this)
        }

        val entityOnLocal = runBlocking {
            localRepDb.repDao.findByUidLive(entity.rePrimaryKey).waitUntilWithTimeout(5000) {
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


    @Test
    fun givenReplicationSubscriptionEnabled_whenInsertedOnLocal_thenShouldTransferToRemoteAgain() {
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
    fun givenTwoActiveClients_whenEntityInsertedOnClient1_thenShouldReplicateToClient2() {
        setupLocalDb2()

        val startTime = systemTimeInMillis()

        val entity = RepEntity().apply {
            reString = "Subscribe and replicate"
            rePrimaryKey = localRepDb.repDao.insert(this)
        }

        val entityOnRemote = runBlocking {
            remoteRepDb.repDao.findByUidLive(entity.rePrimaryKey).waitUntilWithTimeout(10000) {
                it != null
            }
        }

        val entityOnLocal2 = runBlocking {
            localRepDb2.repDao.findByUidLive(entity.rePrimaryKey).waitUntilWithTimeout(10000) {
                it != null
            }
        }

        val runTime = systemTimeInMillis() - startTime
        Napier.d("Finish sync after $runTime ms")
        Assert.assertNotNull("Got entity on remote", entityOnRemote)
        Assert.assertNotNull("Got entity on local 2", entityOnLocal2)
    }

    @Test
    fun givenTwoActiveClients_whenEntityCreatedOnServer_thenShouldReplicateToBothClients() {
        setupLocalDb2()

        val entity = RepEntity().apply {
            reString = "Subscribe and replicate"
            rePrimaryKey = remoteRepDb.repDao.insert(this)
        }

        val entityOnLocal1 = runBlocking {
            localRepDb.repDao.findByUidLive(entity.rePrimaryKey).waitUntilWithTimeout(5000) {
                it != null
            }
        }

        val entityOnLocal2 = runBlocking {
            localRepDb2.repDao.findByUidLive(entity.rePrimaryKey).waitUntilWithTimeout(5000) {
                it != null
            }
        }


        Assert.assertNotNull("Entity was replicated to local1", entityOnLocal1)
        Assert.assertNotNull("Entity was replicated to local2", entityOnLocal2)
    }

    @Test
    fun givenTwoActiveClients_whenEntityCreatedOnLocal1ThenUpdatedOnLocal2_thenUpdatedVersionShouldReplicateToLocalAndRemote() {
        setupLocalDb2()

        val entity = RepEntity().apply {
            reString = "Subscribe and replicate"
            rePrimaryKey = localRepDb.repDao.insert(this)
        }

        runBlocking {
            remoteRepDb.repDao.findByUidLive(entity.rePrimaryKey).waitUntilWithTimeout(10000) {
                it != null
            }
        }

        val entityOnLocal2 = runBlocking {
            localRepDb2.repDao.findByUidLive(entity.rePrimaryKey).waitUntilWithTimeout(10000) {
                it != null
            }
        }

        //now update on local2
        localRepDb2.repDao.update(entityOnLocal2!!.apply {
            reString = "Updated"
        })

        val updatedOnRemote = runBlocking {
            remoteRepDb.repDao.findByUidLive(entity.rePrimaryKey).waitUntilWithTimeout(5000) {
                it?.reString == "Updated"
            }
        }

        val updatedOnLocal1 = runBlocking {
            localRepDb.repDao.findByUidLive(entity.rePrimaryKey).waitUntilWithTimeout(5001) {
                it?.reString == "Updated"
            }
        }

        Assert.assertEquals("Entity was updated on remote", "Updated", updatedOnRemote?.reString)
        Assert.assertEquals("Entity was updated on local1", "Updated", updatedOnLocal1?.reString)
    }

    @Test
    fun givenTwoActiveClients_whenEntityCreatedAndThenUpdatedOnLocalOne_thenLatestVersionShouldReplicate() {
        setupLocalDb2()

        val entity = RepEntity().apply {
            reString = "Subscribe and replicate"
            rePrimaryKey = localRepDb.repDao.insert(this)
        }



    }


    companion object {

        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            Napier.takeLogarithm()
            Napier.base(DebugAntilog())
        }

        private const val REMOTE_NODE_ID = 1234L

        private const val LOCAL_NODE_ID = 1330L

        private const val LOCAL_NODE_ID2 = 1331L
    }

}