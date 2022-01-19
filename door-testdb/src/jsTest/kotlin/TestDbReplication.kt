import com.ustadmobile.door.*
import com.ustadmobile.door.ext.asRepository
import com.ustadmobile.door.ext.rootDatabase
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import io.ktor.client.*
import io.ktor.client.engine.js.*
import kotlinx.browser.window
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.w3c.dom.url.URL
import org.w3c.files.Blob
import repdb.RepDb
import repdb.RepDbJsImplementations
import repdb.RepEntity
import kotlin.js.Promise
import kotlin.test.Test
import kotlin.test.assertNotNull

class TestDbReplication {

    private lateinit var repDb: RepDb

    private lateinit var repRepo: RepDb

    private lateinit var httpClient: HttpClient

    private suspend fun openRepoDb() {
        Napier.base(DebugAntilog())
        val res = (window.fetch("""
            https://raw.githubusercontent.com/UstadMobile/door/dev-js-2/app-testdb/src/main/resources/worker.sql-asm.js
        """.trimIndent()) as Promise<dynamic>).await()
        val data = (res.blob() as Promise<dynamic>).await()
        val workerBlobUrl = URL.createObjectURL(data as Blob)

        val builderOptions = DatabaseBuilderOptions(
            RepDb::class, RepDbJsImplementations, "resDb", workerBlobUrl)
        httpClient = HttpClient(Js) { }

        Napier.d("Creating db and repo")
        repDb = DatabaseBuilder.databaseBuilder(builderOptions).build()
        Napier.d("Created db")
        Napier.d("Db name = ${(repDb.rootDatabase as DoorDatabaseJdbc).dbName}\n")
        Napier.d("Replicate wrapper name = ${(repDb as DoorDatabaseReplicateWrapper).dbName}\n")
        Napier.d("repDb toString = $repDb\n")
        repRepo = repDb.asRepository(RepositoryConfig.repositoryConfig(Any(), "http://localhost:8098/RepDb/",
            "secret", 1234L, httpClient, Json { encodeDefaults = true }) {

        })
        Napier.d("Created repo")
    }

    @Test
    fun shouldConnectToReplicate() = GlobalScope.promise {
        openRepoDb()


        val completable = CompletableDeferred<RepEntity>()
        repDb.repDao.findByUidLive(42L).observeForever {
            if(it != null)
                completable.complete(it)
        }
        val entityFromServer = withTimeout(1000) {
            completable.await()
        }
        //assertNotNull(entityFromServer, message = "Entity from server should not be null")



//        val repDbRepo = repDb.asRepository(RepositoryConfig.repositoryConfig(Any(),
//            "http://localhost:8097/RepDb/", "", ))
    }

}