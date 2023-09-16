package com.ustadmobile.door.ktor.routes

import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.door.DoorConstants
import com.ustadmobile.door.http.DbAndDao
import com.ustadmobile.door.http.DoorHttpServerConfig
import com.ustadmobile.door.message.DoorMessage
import db3.DiscussionPost
import db3.DiscussionPostDao_KtorRoute
import db3.ExampleDb3
import db3.Member
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Test that a generated HTTP endpoint provides the expected http responses.
 */
class GeneratedHttpDaoEndpointTest {

    data class PullReplicationTestContext(
        val db: ExampleDb3,
        val testClient: HttpClient,
        val json: Json,
    )

    private val serverNodeId = 1L

    private val clientNodeId = 2L

    private fun testHttpEndpoint(
        block: suspend ApplicationTestBuilder.(PullReplicationTestContext) -> Unit
    ) {
        val db = DatabaseBuilder.databaseBuilder(ExampleDb3::class, "jdbc:sqlite::memory:", serverNodeId)
            .build()
        db.clearAllTables()

        val json = Json {
            encodeDefaults = true
        }

        testApplication {
            environment {
                config = MapApplicationConfig("ktor.environment" to "test")
            }

            @Suppress("RemoveRedundantQualifierName", "RedundantSuppression") //Ensure clarity between client and server
            install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
                json(json = json)
            }

            routing {
                val serverConfig = DoorHttpServerConfig(
                    json = json
                )

                DiscussionPostDao_KtorRoute(serverConfig) {
                    DbAndDao(db, db.discussionPostDao)
                }
            }

            val client = createClient {
                @Suppress("RemoveRedundantQualifierName", "RedundantSuppression") //Ensure clarity between client and server
                install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                    json(json = json)
                }
            }

            val context = PullReplicationTestContext(db, client, json)
            try {
                block(context)
            }catch(e: Exception) {
                e.printStackTrace()
            } finally {
                db.close()
            }
        }
    }


    @Test
    fun givenEntitiesInServer_whenRequestFunctionThatReturnsReplicateEntities_thenWillRespondWithReplicateEntities() {
        testHttpEndpoint { context ->
            val memberInDb = Member().apply {
                firstName = "Bugs"
                lastName = "Bunny"
                memberUid = context.db.memberDao.insertAsync(this)
            }

            val postInDb = DiscussionPost().apply {
                postTitle = "Demo post"
                postText = "Hello World"
                posterMemberUid = memberInDb.memberUid
                postUid = context.db.discussionPostDao.insertAsync(this)
            }

            val response = context.testClient.get("/findByUidWithPosterMember") {
                header(DoorConstants.HEADER_NODE_AND_AUTH, "$clientNodeId/secret")
                parameter("postUid", postInDb.postUid)
            }

            val doorMessage: DoorMessage = response.body()
            val memberReplicateEntity = doorMessage.replications.first { it.tableId == Member.TABLE_ID }
            val memberFromRequest = context.json.decodeFromJsonElement(Member.serializer(), memberReplicateEntity.entity)
            assertEquals(memberInDb, memberFromRequest)

            val postReplicateEntity = doorMessage.replications.first { it.tableId == DiscussionPost.TABLE_ID }
            val postFromRequest = context.json.decodeFromJsonElement(DiscussionPost.serializer(), postReplicateEntity.entity)
            assertEquals(postInDb, postFromRequest)

            assertEquals(serverNodeId, doorMessage.fromNode)
            assertEquals(clientNodeId, doorMessage.toNode)
        }
    }

}