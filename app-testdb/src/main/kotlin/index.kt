import com.ustadmobile.door.*
import com.ustadmobile.door.ext.asRepository
import com.ustadmobile.door.ext.rootDatabase
import com.ustadmobile.door.util.systemTimeInMillis
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import io.ktor.client.*
import io.ktor.client.engine.js.*
import io.ktor.client.request.*
import kotlinx.browser.window
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.w3c.dom.url.URL
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag
import repdb.RepDb
import repdb.RepDbJsImplementations
import repdb.RepEntity
import repdb.RepEntityWithAttachment
import kotlin.js.Date
import kotlin.js.Promise
import kotlin.random.Random

/**
 * Run this using door-testdb-server
 */

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
    repDb = DatabaseBuilder.databaseBuilder(builderOptions).build().also {
        it.clearAllTablesAsync()
    }
    Napier.d("Created db")
    Napier.d("Db name = ${(repDb.rootDatabase as DoorDatabaseJdbc).dbName}\n")
    Napier.d("Replicate wrapper name = ${(repDb as DoorDatabaseReplicateWrapper).dbName}\n")
    Napier.d("repDb toString = $repDb\n")
    val nodeId = Random.nextLong(0, Long.MAX_VALUE)
    repRepo = repDb.asRepository(
        RepositoryConfig.repositoryConfig(Any(), "http://localhost:8098/RepDb/",
        "secret", nodeId, httpClient, Json { encodeDefaults = true }) {
    })
    Napier.d("Created repo")
}

fun main() {
    window.onload = {
//        render(document.getElementById("root")!!) {
//            renderApp()
//        }
        GlobalScope.launch {
            openRepoDb()

            Napier.i("IndexTest: Inserting into repDb")
            repDb.repDao.insertAsync(RepEntity().apply {
                this.reString = "From client at ${Date().toUTCString()} UTC"
                this.reNumField = 500
            })

            Napier.i("IndexTest: Inserted into repDb")


            delay(5000)
            val entityAsyncQueryResult = repDb.repDao.findByUidAsync(42L)

            console.log("IndexTest: GOT ENTITY FROM SERVER:? $entityAsyncQueryResult")

            console.log("IndexTest: Asking server to make something")
            val serverInsertUid = systemTimeInMillis()
            val response = httpClient.get<String>(
                "http://localhost:8098/insertRepEntity") {
                parameter("rePrimaryKey", serverInsertUid)
                parameter("reString", "From server at ${Date().toUTCString()}")
            }

            console.log("IndexTest: Server says: $response ... Waiting...")

            val completableDeferred = CompletableDeferred<RepEntity>()
            val observer = DoorObserver<RepEntity?> {
                if(it != null)
                    completableDeferred.complete(it)
            }
            repDb.repDao.findByUidLive(serverInsertUid).observeForever(observer)
            val entityReceived = withTimeout(5000) {
                completableDeferred.await()
            }

            console.log("IndexTest: Got it back! $entityReceived")

            val attachmentBlob = Blob(arrayOf("Hello World ${systemTimeInMillis()}"),
                BlobPropertyBag(type = "text/string"))
            console.log("IndexTest: Stored attachment")
            val entityWithAttachment = RepEntityWithAttachment().apply {
                waAttachmentUri = URL.createObjectURL(attachmentBlob)
            }
            repDb.repWithAttachmentDao.insertAsync(entityWithAttachment)
            console.log("IndexTest: Stored attachment")

            //repDb.exportDatabase()
        }

        Unit

    }
}