package com.ustadmobile.door.replication

import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.door.RepositoryConfig
import com.ustadmobile.door.ext.asRepository
import com.ustadmobile.door.ext.doorWrapperNodeId
import com.ustadmobile.door.http.DoorHttpServerConfig
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
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert
import org.junit.Test

class PullIntegrationTest {

    data class ClientServerIntegrationTestContext(
        val serverDb: ExampleDb3,
        val clientDb: ExampleDb3,
        val client: HttpClient,
        val okHttpClient: OkHttpClient,
        val serverEndpointUrl: String,
        val json: Json,
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

            val discussionAndMember = clientRepo.discussionPostDao.findByUidWithPosterMember(discussionPostInServerDb.postUid)

            Assert.assertEquals(memberInServerDb, discussionAndMember?.posterMember)
            Assert.assertEquals(discussionPostInServerDb, discussionAndMember?.discussionPost)
        }
    }



}