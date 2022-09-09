package com.ustadmobile.door.testandroid

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.door.RepositoryConfig
import com.ustadmobile.door.RepositoryConfig.Companion.repositoryConfig
import com.ustadmobile.door.ext.asRepository
import com.ustadmobile.door.replication.ReplicationSubscriptionMode
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.gson.*
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import repdb.RepDb
import repdb.RepEntity
import kotlin.random.Random


class ReplicationTest {

    private lateinit var repo: RepDb

    private lateinit var db: RepDb

    private lateinit var httpClient: HttpClient

    private lateinit var okHttpClient: OkHttpClient


    @JvmField
    @Rule
    var temporaryFolder = TemporaryFolder()


    @Before
    fun setup() {
        Napier.base(DebugAntilog())
        val context = ApplicationProvider.getApplicationContext<Context>()
        okHttpClient = OkHttpClient.Builder()
            .dispatcher(Dispatcher().also {
                it.maxRequests = 30
                it.maxRequestsPerHost = 10
            })
            .build()

        httpClient = HttpClient(OkHttp) {

            install(ContentNegotiation) {
                gson {

                }
            }
            install(HttpTimeout)

            val dispatcher = Dispatcher()
            dispatcher.maxRequests = 30
            dispatcher.maxRequestsPerHost = 10
            engine {
                preconfigured = okHttpClient
            }
        }

        db = DatabaseBuilder.databaseBuilder(context, RepDb::class, "RepDb",
            temporaryFolder.newFolder()).build()
        repo = db.asRepository(repositoryConfig(context, "http://192.168.1.148:8098/RepDb/",
            Random.nextLong(), "af", httpClient, okHttpClient)
        {
            useReplicationSubscription = true

        })
    }

    @Test
    fun givenRepoOpened_whenWaiting_thenShouldSync() {
        db.repDao.insert(RepEntity().apply {
            this.reString = "hello client"
        })
        Thread.sleep(10000)
    }
}