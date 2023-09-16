package com.ustadmobile.door.replication

import app.cash.paging.PagingSourceLoadParamsRefresh
import app.cash.paging.PagingSourceLoadResultPage
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.door.RepositoryConfig
import com.ustadmobile.door.ext.asRepository
import com.ustadmobile.door.ext.doorWrapperNodeId
import com.ustadmobile.door.ext.use
import com.ustadmobile.door.http.DoorHttpServerConfig
import com.ustadmobile.door.flow.FlowLoadingState
import com.ustadmobile.door.flow.repoFlowWithLoadingState
import com.ustadmobile.door.test.initNapierLog
import db3.*
import io.ktor.client.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

class PullIntegrationTest {

    data class ClientServerIntegrationTestContext(
        val serverDb: ExampleDb3,
        val clientDb: ExampleDb3,
        val client: HttpClient,
        val okHttpClient: OkHttpClient,
        val serverEndpointUrl: String,
        val json: Json,
        val server: ApplicationEngine,
    ) {

        fun makeClientRepo(): ExampleDb3 {
            return clientDb.asRepository(RepositoryConfig.repositoryConfig(
                context = Any(),
                endpoint = serverEndpointUrl,
                nodeId = clientDb.doorWrapperNodeId,
                auth = "secret",
                httpClient = client,
                okHttpClient = okHttpClient,
                json = json,
            ))
        }

    }

    fun clientServerIntegrationTest(
        serverNodeId: Long = 1L,
        clientNodeId: Long = 2L,
        block: suspend ClientServerIntegrationTestContext.() -> Unit
    ) {
        val clientDb = DatabaseBuilder.databaseBuilder(ExampleDb3::class, "jdbc:sqlite::memory:", clientNodeId)
            .build()
        val serverDb = DatabaseBuilder.databaseBuilder(ExampleDb3::class, "jdbc:sqlite::memory:", serverNodeId)
            .build()

        val okHttpClient = OkHttpClient.Builder().build()
        val json = Json {
            encodeDefaults = true
        }

        val httpClient = HttpClient {
            @Suppress("RemoveRedundantQualifierName", "RedundantSuppression") //Ensure clarity between client and server
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json(json = json)
            }
        }

        val serverConfig = DoorHttpServerConfig(json = json)
        val server = embeddedServer(Netty, 8094) {
            routing {
                ExampleDb3_KtorRoute(serverConfig) { serverDb }
            }
        }
        server.start()

        runBlocking {
            try {
                block(
                    ClientServerIntegrationTestContext(
                        serverDb = serverDb,
                        clientDb = clientDb,
                        client = httpClient,
                        okHttpClient = okHttpClient,
                        serverEndpointUrl = "http://localhost:8094/",
                        json = json,
                        server = server,
                    )
                )
            }finally {
                httpClient.close()
                server.stop()
                clientDb.close()
                serverDb.close()
            }
        }
    }

    @Test
    fun givenEntityOnServer_whenClientMakesPullRequest_repoFunctionReturnsValuesAndEntitiesAreStoredInClientLocalDb() {
        initNapierLog()
        clientServerIntegrationTest {
            val memberInServerDb = Member().apply {
                firstName = "Roger"
                lastName = "Rabbit"
                memberUid = serverDb.memberDao.insertAsync(this)
            }

            val discussionPostInServerDb = DiscussionPost().apply {
                postTitle = "I like hay"
                postText = "Mmm... Hay..."
                posterMemberUid = memberInServerDb.memberUid
                postUid = serverDb.discussionPostDao.insertAsync(this)
            }

            val clientRepo = makeClientRepo()

            val discussionAndMemberInClientDb = clientRepo.discussionPostDao.findByUidWithPosterMember(discussionPostInServerDb.postUid)

            Assert.assertEquals(memberInServerDb, discussionAndMemberInClientDb?.posterMember)
            Assert.assertEquals(discussionPostInServerDb, discussionAndMemberInClientDb?.discussionPost)
            clientRepo.close()
        }
    }

    @Test
    fun givenEntitiesCreatedOnServer_whenClientUsesHttpWithFallbackFunction_thenWillReturnAnswersAndNotCopyToLocalDatabase() {
        clientServerIntegrationTest {
            val memberInServerDb = Member().apply {
                firstName = "Roger"
                lastName = "Rabbit"
                memberUid = serverDb.memberDao.insertAsync(this)
            }

            DiscussionPost().apply {
                postTitle = "I like hay"
                postText = "Mmm... Hay..."
                posterMemberUid = memberInServerDb.memberUid
                postUid = serverDb.discussionPostDao.insertAsync(this)
            }

            val clientRepo = makeClientRepo()

            val numPostsFromServer = clientRepo.discussionPostDao.getNumPostsSinceTime(0)
            Assert.assertEquals(1, numPostsFromServer)
            val numPostsLocally = clientDb.discussionPostDao.getNumPostsSinceTime(0)
            assertEquals(0, numPostsLocally)
            clientRepo.close()
        }
    }

    @Test
    fun givenEntitiesCreatedOnServer_whenClientUsesHttpWithoutFallback_thenWillReturnAnswersAndNotCopyToLocalDatabase() {
        clientServerIntegrationTest {
            val memberInServerDb = Member().apply {
                firstName = "Roger"
                lastName = "Rabbit"
                memberUid = serverDb.memberDao.insertAsync(this)
            }

            DiscussionPost().apply {
                postTitle = "I like hay"
                postText = "Mmm... Hay..."
                posterMemberUid = memberInServerDb.memberUid
                postUid = serverDb.discussionPostDao.insertAsync(this)
            }

            val clientRepo = makeClientRepo()

            val numPostsFromServer = clientRepo.discussionPostDao.getNumPostsSinceTimeHttpOnly(0)
            Assert.assertEquals(1, numPostsFromServer)
            val numPostsLocally = clientDb.discussionPostDao.getNumPostsSinceTimeHttpOnly(0)
            assertEquals(0, numPostsLocally)
            clientRepo.close()
        }
    }

    @Test(expected = Exception::class)
    fun givenEntitiesCreatedOnServer_whenClientUsesHttpWithoutFallbackAndServerIsUnreachable_thenWillThrowException(){
        clientServerIntegrationTest {
            val memberInServerDb = Member().apply {
                firstName = "Roger"
                lastName = "Rabbit"
                memberUid = serverDb.memberDao.insertAsync(this)
            }

            DiscussionPost().apply {
                postTitle = "I like hay"
                postText = "Mmm... Hay..."
                posterMemberUid = memberInServerDb.memberUid
                postUid = serverDb.discussionPostDao.insertAsync(this)
            }

            makeClientRepo().use { clientRepo ->
                server.stop()
                clientRepo.discussionPostDao.getNumPostsSinceTimeHttpOnly(0)
            }

        }
    }

    suspend fun <T> ReceiveTurbine<T>.awaitItemWhere(
        block: (T) -> Boolean
    ) : T {
        while(currentCoroutineContext().isActive) {
            val item = awaitItem()
            if(block(item))
                return item
        }

        throw CancellationException("Item not received and no longer active")
    }

    @Test
    fun givenEntitiesCreatedOnServer_whenClientUsesFlow_thenFlowWillUpdateAndEntitiesWillBeCopiedToLocalDb() {
        clientServerIntegrationTest {
            val memberInServerDb = Member().apply {
                firstName = "Roger"
                lastName = "Rabbit"
                memberUid = serverDb.memberDao.insertAsync(this)
            }

            val post = DiscussionPost().apply {
                postTitle = "I like hay"
                postText = "Mmm... Hay..."
                posterMemberUid = memberInServerDb.memberUid
                postUid = serverDb.discussionPostDao.insertAsync(this)
            }

            val clientRepo = makeClientRepo()

            clientRepo.discussionPostDao.findByUidWithPosterMemberAsFlow(
                post.postUid
            ).test(timeout = 5.seconds) {
                val itemLoaded = awaitItemWhere { it != null }
                assertEquals(post, itemLoaded?.discussionPost)
                assertEquals(memberInServerDb, itemLoaded?.posterMember)
                cancelAndIgnoreRemainingEvents()
            }

            val postInClientDb = clientDb.discussionPostDao.findByUidWithPosterMember(post.postUid)
            assertEquals(post, postInClientDb?.discussionPost)
            assertEquals(memberInServerDb, postInClientDb?.posterMember)
            clientRepo.close()
        }
    }

    @Test
    fun givenEntitiesCreatedOnServer_whenClientUsesFlowWithLoadingStatus_thenWillUpdateLoadingStatusAndWillBeCopiedToDb() {
        clientServerIntegrationTest {
            val memberInServerDb = Member().apply {
                firstName = "Roger"
                lastName = "Rabbit"
                memberUid = serverDb.memberDao.insertAsync(this)
            }

            val post = DiscussionPost().apply {
                postTitle = "I like hay"
                postText = "Mmm... Hay..."
                posterMemberUid = memberInServerDb.memberUid
                postUid = serverDb.discussionPostDao.insertAsync(this)
            }

            val clientRepo = makeClientRepo()

            clientRepo.discussionPostDao.repoFlowWithLoadingState {
                it.findByUidWithPosterMemberAsFlow(post.postUid)
            }.test(timeout = 500.seconds) {
                val initialState = awaitItemWhere { it.loadingState?.status == FlowLoadingState.Status.LOADING }

                @Suppress("UNUSED_VARIABLE")
                val loadedState = awaitItemWhere { it.loadingState?.status == FlowLoadingState.Status.DONE }

                val postInClientDb = clientDb.discussionPostDao.findByUidWithPosterMember(post.postUid)

                assertEquals(FlowLoadingState.Status.LOADING, initialState.loadingState?.status)
                assertEquals(post, postInClientDb?.discussionPost)
                assertEquals(memberInServerDb, postInClientDb?.posterMember)
                cancelAndIgnoreRemainingEvents()
            }
            clientRepo.close()
        }
    }

    @Test
    fun givenEntitiesCreatedOnServer_whenClientUsesPagingSource_thenWillLoad() {
        clientServerIntegrationTest {
            val memberInServerDb = Member().apply {
                firstName = "Roger"
                lastName = "Rabbit"
                memberUid = serverDb.memberDao.insertAsync(this)
            }

            val post = DiscussionPost().apply {
                postTitle = "I like hay"
                postText = "Mmm... Hay..."
                posterMemberUid = memberInServerDb.memberUid
                postUid = serverDb.discussionPostDao.insertAsync(this)
            }

            val clientRepo = makeClientRepo()

            clientRepo.discussionPostDao.findAllPostAsPagingSource(0).load(PagingSourceLoadParamsRefresh(
                key = 0, loadSize = 50, placeholdersEnabled = false
            ))

            clientDb.discussionPostDao.findByUidWithPosterMemberAsFlow(post.postUid)
                .filter { it != null }
                .test(timeout = 5.seconds) {
                    awaitItem()
                }

            clientRepo.close()
        }
    }


    @Test
    fun givenEntitiesCreatedOnServer_whenClientUsesNetworkOnlyPagingSource_thenWillLoadAndNotInsertLocally() {
        clientServerIntegrationTest {
            val memberInServerDb = Member().apply {
                firstName = "Roger"
                lastName = "Rabbit"
                memberUid = serverDb.memberDao.insertAsync(this)
            }

            val post = DiscussionPost().apply {
                postTitle = "I like hay"
                postText = "Mmm... Hay..."
                posterMemberUid = memberInServerDb.memberUid
                postUid = serverDb.discussionPostDao.insertAsync(this)
            }

            val clientRepo = makeClientRepo()
            val networkOnlyPagingSource = clientRepo.discussionPostDao.findAllPostAsNetworkOnlyPagingSource(0)
            val firstLoad = networkOnlyPagingSource.load(
                PagingSourceLoadParamsRefresh(
                    key = 0,
                    loadSize = 50,
                    placeholdersEnabled = false
                )
            ) as PagingSourceLoadResultPage<Int, DiscussionPost>
            assertEquals(post, firstLoad.data.first())
            assertNull(clientDb.discussionPostDao.findByUid(post.postUid))
            clientRepo.close()
        }
    }

}