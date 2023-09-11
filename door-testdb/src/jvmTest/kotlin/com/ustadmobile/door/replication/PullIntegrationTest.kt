package com.ustadmobile.door.replication

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.door.RepositoryConfig
import com.ustadmobile.door.ext.asRepository
import com.ustadmobile.door.ext.doorWrapperNodeId
import com.ustadmobile.door.http.DoorHttpServerConfig
import com.ustadmobile.door.http.LoadingState
import com.ustadmobile.door.http.repoFlowWithLoadingState
import db3.DiscussionPost
import db3.ExampleDb3
import db3.ExampleDb3_KtorRoute
import db3.Member
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import io.ktor.client.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert
import org.junit.Test
import kotlin.test.assertEquals
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
            }
        }
    }

    @Test
    fun givenEntityOnServer_whenClientMakesPullRequest_repoFunctionReturnsValuesAndEntitiesAreStoredInClientLocalDb() {
        Napier.base(DebugAntilog())
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

            val clientRepo = makeClientRepo()

            server.stop()
            clientRepo.discussionPostDao.getNumPostsSinceTimeHttpOnly(0)

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
                val initialState = awaitItemWhere { it.loadingState?.status == LoadingState.Status.LOADING }

                val loadedState = awaitItemWhere { it.loadingState?.status == LoadingState.Status.DONE }

                val postInClientDb = clientDb.discussionPostDao.findByUidWithPosterMember(post.postUid)

                assertEquals(LoadingState.Status.LOADING, initialState.loadingState?.status)
                assertEquals(post, postInClientDb?.discussionPost)
                assertEquals(memberInServerDb, postInClientDb?.posterMember)
                cancelAndIgnoreRemainingEvents()
            }
        }
    }


}