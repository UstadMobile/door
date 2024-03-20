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
import com.ustadmobile.door.flow.FlowLoadingState
import com.ustadmobile.door.flow.doorFlow
import com.ustadmobile.door.flow.repoFlowWithLoadingState
import com.ustadmobile.door.http.DoorHttpServerConfig
import com.ustadmobile.door.log.NapierDoorLogger
import com.ustadmobile.door.room.InvalidationTrackerObserver
import com.ustadmobile.door.test.initNapierLog
import db3.*
import io.ktor.client.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
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
                logger = NapierDoorLogger(),
                dbName = "clientDb",
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
            .name("clientDb")
            .build()
        val serverDb = DatabaseBuilder.databaseBuilder(ExampleDb3::class, "jdbc:sqlite::memory:", serverNodeId)
            .name("serverDb")
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

    @Test
    fun givenEntitiesCreatedOnServer_whenClientUsesHttpWithoutFallbackToRequestPrimitive_thenWillReturnResult() {
        clientServerIntegrationTest {
            val memberInServerDb = Member().apply {
                firstName = "Roger"
                lastName = "Rabbit"
                memberUid = serverDb.memberDao.insertAsync(this)
            }


            makeClientRepo().use { clientRepo ->
                val firstNameFromHttp = clientRepo.memberDao.getFirstNameByMemberId(
                    memberInServerDb.memberUid
                )
                assertEquals(memberInServerDb.firstName, firstNameFromHttp,
                        message = "Requesting string over http function matches")
            }
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

    @Test
    fun givenEntitiesCreatedOnServer_whenExtraPullQueriesToReplicateAreSpecified_thenWillLoadExtraData() {
        clientServerIntegrationTest {
            val memberInServerDb = Member().apply {
                firstName = "Roger"
                lastName = "Rabbit"
                memberUid = serverDb.memberDao.insertAsync(this)
            }

            val originalPost = DiscussionPost().apply {
                postTitle = "I like hay"
                postText = "Mmm... Hay..."
                posterMemberUid = memberInServerDb.memberUid
                postUid = serverDb.discussionPostDao.insertAsync(this)
            }

            val replyPost = DiscussionPost().apply {
                postTitle = "I also like hay"
                postText = "Mmm... Hay..."
                posterMemberUid = memberInServerDb.memberUid
                postReplyToPostUid = originalPost.postUid
                postUid = serverDb.discussionPostDao.insertAsync(this)
            }

            makeClientRepo().use { clientRepo ->
                val result = clientRepo.discussionPostDao.findPostAndNumReplies(originalPost.postUid, 0L)

                //Return result from the function itself should match what we expect: e.g. contains the original post,
                // and there is one reply
                assertEquals(originalPost, result?.discussionPost)
                assertEquals(1, result?.numReplies ?: -1)

                //Both replies should now be in the database
                val originalPostInLocalDb = clientDb.discussionPostDao.findByUid(originalPost.postUid)
                assertEquals(originalPost, originalPostInLocalDb)

                val replyPostInLocalDb = clientDb.discussionPostDao.findByUid(replyPost.postUid)
                assertEquals(replyPost, replyPostInLocalDb)
            }
        }
    }

    @Test
    fun givenEntitiesCreatedOnServer_whenExtraPullQueriesToReplicateAreSpecifiedForPagingSource_thenWillLoadExtraData() {
        clientServerIntegrationTest {
            val memberInServerDb = Member().apply {
                firstName = "Roger"
                lastName = "Rabbit"
                memberUid = serverDb.memberDao.insertAsync(this)
            }

            val originalPost = DiscussionPost().apply {
                postTitle = "I like hay"
                postText = "Mmm... Hay..."
                posterMemberUid = memberInServerDb.memberUid
                postUid = serverDb.discussionPostDao.insertAsync(this)
            }

            val replyPost = DiscussionPost().apply {
                postTitle = "I also like hay"
                postText = "Mmm... Hay..."
                posterMemberUid = memberInServerDb.memberUid
                postReplyToPostUid = originalPost.postUid
                postUid = serverDb.discussionPostDao.insertAsync(this)
            }

            makeClientRepo().use { clientRepo ->
                clientRepo.discussionPostDao.findRootPostAndNumRepliesAsPagingSourceWithPagedParams().load(
                    PagingSourceLoadParamsRefresh(
                        key = 0, loadSize = 50, placeholdersEnabled = false
                    )
                )

                clientDb.discussionPostDao.findRootPostAndNumRepliesAsPagingSourceWithAsFlow().filter { postAndReplyList: List<DiscussionPostAndNumReplies> ->
                    postAndReplyList.any { it.discussionPost?.postUid ==  originalPost.postUid && it.numReplies == 1 }
                }.test(timeout = 10.seconds) {
                    assertTrue(awaitItem().isNotEmpty())
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }
    }

    @Test
    fun givenEntitiesCreatedOnServer_whenAuthCheckIsFailed_thenWillNotPullAndInsert() {
        clientServerIntegrationTest {
            val originalPost = DiscussionPost().apply {
                postTitle = "I like hay"
                postText = "Mmm... Hay..."
                posterMemberUid = 1
                postUid = serverDb.discussionPostDao.insertAsync(this)
            }

            val replyPost = DiscussionPost().apply {
                postTitle = "I also like hay"
                postText = "Mmm... Hay..."
                posterMemberUid = 1
                postReplyToPostUid = originalPost.postUid
                postUid = serverDb.discussionPostDao.insertAsync(this)
            }

            val hasPermissionOnServer = serverDb.discussionPostDao.checkNodeHasPermission(
                originalPost.postUid, clientDb.doorWrapperNodeId)
            println(hasPermissionOnServer)

            makeClientRepo().use { clientRepo ->
                clientRepo.discussionPostDao.findRepliesWithAuthCheck(originalPost.postUid)
                assertNull(clientDb.discussionPostDao.findByUid(replyPost.postUid))
            }

        }
    }

    @Test
    fun givenEntitiesCreatedOnServer_whenAuthCheckIsPassed_thenWillPullAndInsert() {
        //The auth check simply checks if the client node id matches the postUid
        val memberAndClientUid = 5L
        clientServerIntegrationTest(clientNodeId = memberAndClientUid) {
            val memberInServerDb = Member().apply {
                firstName = "Roger"
                lastName = "Rabbit"
                memberUid = memberAndClientUid
                serverDb.memberDao.insertAsync(this)
            }

            val originalPost = DiscussionPost().apply {
                postTitle = "I like hay"
                postText = "Mmm... Hay..."
                posterMemberUid = memberInServerDb.memberUid
                postUid = memberAndClientUid
                serverDb.discussionPostDao.insertAsync(this)
            }

            val replyPost = DiscussionPost().apply {
                postTitle = "I also like hay"
                postText = "Mmm... Hay..."
                posterMemberUid = memberInServerDb.memberUid
                postReplyToPostUid = originalPost.postUid
                postUid = serverDb.discussionPostDao.insertAsync(this)
            }

            makeClientRepo().use { clientRepo ->
                val clientRepliesWithCheck = clientRepo.discussionPostDao.findRepliesWithAuthCheck(originalPost.postUid)
                assertEquals(replyPost, clientRepliesWithCheck.first())
                assertEquals(replyPost, clientDb.discussionPostDao.findByUid(replyPost.postUid))
            }
        }
    }

    /**
     * Test use of an HttpServerFunction that includes getting data from a function on another DAO
     */
    @Test
    fun givenEntitiesCreatedOnServer_whenQueryFromOtherDaoIsUsed_thenWillPullAndInsert() {
        clientServerIntegrationTest {
            val memberInServerDb = Member().apply {
                firstName = "Roger"
                lastName = "Rabbit"
                memberUid = serverDb.memberDao.insertAsync(this)

            }

            val originalPost = DiscussionPost().apply {
                postTitle = "I like hay"
                postText = "Mmm... Hay..."
                posterMemberUid = memberInServerDb.memberUid
                postUid = serverDb.discussionPostDao.insertAsync(this)
            }

            makeClientRepo().use { clientRepo ->
                val postAndName = clientRepo.discussionPostDao.getDiscussionPostAndAuthorName(originalPost.postUid)
                assertEquals(originalPost, postAndName?.discussionPost)
                assertEquals(memberInServerDb.firstName, postAndName?.firstName)
                assertEquals(memberInServerDb.lastName, postAndName?.lastName)
                assertEquals(originalPost, clientDb.discussionPostDao.findByUid(originalPost.postUid))
                assertEquals(memberInServerDb, clientRepo.memberDao.findByUid(memberInServerDb.memberUid))
            }
        }
    }

    @Test
    fun givenEntityCreatedOnServer_whenEntityIsChildOfReplicateClass_thenWillPullAndInsert() {
        clientServerIntegrationTest {
            val badgeInServerDb = Badge().apply {
                badgeName = "star"
                badgeUid = serverDb.badgeDao.insertAsync(this)
            }

            makeClientRepo().use { clientRepo ->
                val badgeFromClient = clientRepo.badgeDao.findBadgeByUid(badgeInServerDb.badgeUid)
                assertEquals(badgeInServerDb.badgeUid, badgeFromClient?.badgeUid)
            }
        }
    }

    /**
     * When an entity is pulled from the server again, the default template should avoid any updates or invalidations
     * when the replicateetag did not change.
     */
    @Test
    fun givenEntityCreatedOnServer_whenEntityIsPulledTwice_thenWillOnlyBeInvalidatedOnce() {
        clientServerIntegrationTest {
            val memberInServerDb = Member().apply {
                firstName = "Roger"
                lastName = "Rabbit"
                memberUid = serverDb.memberDao.insertAsync(this)

            }

            val originalPost = DiscussionPost().apply {
                postTitle = "I like hay"
                postText = "Mmm... Hay..."
                posterMemberUid = memberInServerDb.memberUid
                postUid = serverDb.discussionPostDao.insertAsync(this)
            }

            val invalidationCount = AtomicInteger()
            val invalidationTrackerObserver = object: InvalidationTrackerObserver(arrayOf("DiscussionPost")) {
                override fun onInvalidated(tables: Set<String>) {
                    invalidationCount.incrementAndGet()
                }
            }
            clientDb.invalidationTracker.addObserver(invalidationTrackerObserver)

            makeClientRepo().use { clientRepo ->
                clientRepo.discussionPostDao.getDiscussionPostAndAuthorName(originalPost.postUid)
                val invalidationCountAfterPull1 = invalidationCount.get()
                clientRepo.discussionPostDao.getDiscussionPostAndAuthorName(originalPost.postUid)
                assertTrue(invalidationCountAfterPull1 >= 1,
                    message = "Invalidation count after pulling from server is at least one")
                assertEquals(invalidationCountAfterPull1, invalidationCount.get(),
                        message = "Making a second pull when the entities on the server have not changed does not cause any invalidation")
            }
        }
    }

    @Test
    fun givenEntityCreatedOnServer_whenEntityPulledViaPagingSource_thenWillOnlyBeInvalidatedOnce() {
        initNapierLog()
        clientServerIntegrationTest {
            val memberInServerDb = Member().apply {
                firstName = "Roger"
                lastName = "Rabbit"
                memberUid = serverDb.memberDao.insertAsync(this)

            }

            val originalPost = DiscussionPost().apply {
                postTitle = "I like hay"
                postText = "Mmm... Hay..."
                posterMemberUid = memberInServerDb.memberUid
                postUid = serverDb.discussionPostDao.insertAsync(this)
            }

            val invalidationCountFlow = MutableStateFlow(0)
            val invalidationTrackerObserver = object: InvalidationTrackerObserver(arrayOf("DiscussionPost")) {
                override fun onInvalidated(tables: Set<String>) {
                    invalidationCountFlow.update { prev ->
                        prev + 1
                    }
                }
            }

            clientDb.invalidationTracker.addObserver(invalidationTrackerObserver)

            val scope = CoroutineScope(currentCoroutineContext() + Job())
            makeClientRepo().use { clientRepo ->
                //Simulate a pager in the UI that would request a new paging source every time it gets invalidated
                scope.launch {
                    while(isActive) {
                        val pagingSource = clientRepo.discussionPostDao.findAllPostAsPagingSource(0)
                        pagingSource.load(PagingSourceLoadParamsRefresh(key = null, loadSize = 20, placeholdersEnabled = false))
                        val invalidatedCompletable = CompletableDeferred<Unit>()
                        pagingSource.registerInvalidatedCallback {
                            invalidatedCompletable.complete(Unit)
                        }
                        invalidatedCompletable.await()
                    }
                }

                invalidationCountFlow.filter { it >= 1  }.test(timeout = 5.seconds) {
                    val count = awaitItem()
                    delay(2000)
                    expectNoEvents()
                    cancelAndIgnoreRemainingEvents()
                }


            }
        }
    }

    @Test
    fun givenEntityCreated_whenAccessedOverHttpViaFunctionWithNullableResult_thenWillBeReceived() {
        clientServerIntegrationTest {
            val originalPost = DiscussionPost().apply {
                postTitle = "I like hay"
                postText = "Mmm... Hay..."
                posterMemberUid = 0
                postUid = serverDb.discussionPostDao.insertAsync(this)
            }

            makeClientRepo().use { clientRepo ->
                val receivedOverHttp = clientRepo.discussionPostDao.findByUidAsyncOverHttp(originalPost.postUid)
                assertEquals(originalPost, receivedOverHttp)
            }
        }
    }

    @Test
    fun givenEntityNotCreated_whenAccessedOverHttpFunctionWithNullableResult_thenWillReturnNull() {
        clientServerIntegrationTest {
            makeClientRepo().use { clientRepo ->
                val receivedOverHttp = clientRepo.discussionPostDao.findByUidAsyncOverHttp(0)
                assertNull(receivedOverHttp)
            }
        }
    }



    //@Test
    fun givenEntityCreatedLocally_whenUpdatedAfterSentToServer_thenNowValueWillBeSentToServer() {
        initNapierLog()
        clientServerIntegrationTest {
            makeClientRepo().use { clientRepo ->
                val originalPost = DiscussionPost().apply {
                    postTitle = "I like hay"
                    postText = "Mmm... Hay..."
                    posterMemberUid = 0
                    postUid = clientRepo.discussionPostDao.insertAsync(this)
                }

                //wait for value to reach server
                serverDb.doorFlow(arrayOf("DiscussionPost")) {
                    serverDb.discussionPostDao.findByUid(originalPost.postUid)
                }.filter {
                    it != null
                }.test(timeout = 5.seconds, name = "first post will reach server") {
                    assertEquals(originalPost, awaitItem())
                    cancelAndIgnoreRemainingEvents()
                }

                val updatedPost = originalPost.copy(
                    postText = "Mmm... More Hay..."
                )
                clientRepo.discussionPostDao.update(updatedPost)

                serverDb.doorFlow(arrayOf("DiscussionPost")) {
                    serverDb.discussionPostDao.findByUid(originalPost.postUid)
                }.filter {
                    it?.postText  == updatedPost.postText
                }.test(timeout = 5.seconds, name = "Updated post will reach server") {
                    assertEquals(updatedPost, awaitItem())
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }
    }




}