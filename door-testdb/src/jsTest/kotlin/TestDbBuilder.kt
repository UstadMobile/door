import com.ustadmobile.door.ChangeListenerRequest
import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.door.DatabaseBuilderOptions
import com.ustadmobile.door.ext.addInvalidationListener
import com.ustadmobile.door.util.systemTimeInMillis
import db2.ExampleDatabase2
import db2.ExampleDatabase2JsImplementations
import db2.ExampleDatabase2_JdbcKt
import db2.ExampleEntity2
import kotlinx.browser.window
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.w3c.dom.url.URL
import org.w3c.files.Blob
import repdb.RepDb
import repdb.RepDbJsImplementations
import repdb.RepEntity
import kotlin.js.Promise
import kotlin.test.*

class TestDbBuilder {

     private lateinit var exampleDb2: ExampleDatabase2

     private lateinit var repDb: RepDb

     //We can not use @BeforeTest here, it is async call test will run before it finishes the setting up.
     private suspend fun openClearDb() {
        val res = (window.fetch("""
            https://raw.githubusercontent.com/UstadMobile/door/dev-js-2/app-testdb/src/main/resources/worker.sql-asm.js
        """.trimIndent()) as Promise<dynamic>).await()
        val data = (res.blob() as Promise<dynamic>).await()
        val workerBlobUrl = URL.createObjectURL(data as Blob)
        val builderOptions = DatabaseBuilderOptions(
            ExampleDatabase2::class, ExampleDatabase2JsImplementations, "jsDb1",workerBlobUrl)
        exampleDb2 = DatabaseBuilder.databaseBuilder<ExampleDatabase2>(builderOptions).build().also {
            it.clearAllTablesAsync()
        }
         //TODO Still running synchronously, we need async for this
        //exampleDb2.clearAllTables()
    }

    private suspend fun openRepoDb() {
        val res = (window.fetch("""
            https://raw.githubusercontent.com/UstadMobile/door/dev-js-2/app-testdb/src/main/resources/worker.sql-asm.js
        """.trimIndent()) as Promise<dynamic>).await()
        val data = (res.blob() as Promise<dynamic>).await()
        val workerBlobUrl = URL.createObjectURL(data as Blob)

        val builderOptions = DatabaseBuilderOptions(
            RepDb::class, RepDbJsImplementations, "resDb", workerBlobUrl)
        repDb = DatabaseBuilder.databaseBuilder(builderOptions).build()

    }

    @Test
    fun givenLongInJsonArray_whenDecoded_shouldBeEqualToOriginalValue() = GlobalScope.promise {
        val jsonStr = """[{"big": ${Long.MAX_VALUE}}]"""
        val jsonArray = Json.decodeFromString(JsonArray.serializer(), jsonStr)
        assertEquals(Long.MAX_VALUE, jsonArray.get(0).jsonObject.get("big")?.jsonPrimitive?.long)
    }

    @Test
    fun givenDbShouldOpen() = GlobalScope.promise {
        openClearDb()
        delay(10000)
        val exampleDao2 = exampleDb2.exampleDao2()
        assertNotNull(exampleDao2)
        val exList = listOf(ExampleEntity2(0, "bob",42))
        exampleDao2.insertListAsync(exList)
        println("All done")
        assertTrue(true)
    }

    @Test
    fun givenEntryInserted_whenQueried_shouldBeEqual() = GlobalScope.promise {
        openClearDb()
        val entityToInsert = ExampleEntity2(0, "Bob", 50)
        entityToInsert.uid = exampleDb2.exampleDao2().insertAsyncAndGiveId(entityToInsert)

        val entityFromQuery = exampleDb2.exampleDao2().findByUidAsync(entityToInsert.uid)

        assertNotEquals(0, entityToInsert.uid)
        assertEquals(entityToInsert, entityFromQuery,
            "Entity retrieved from database is the same as entity inserted")
    }

    @Test
    fun givenEntryInserted_whenSingleValueQueried_shouldBeEqual() = GlobalScope.promise{
        openClearDb()
        val entityToInsert = ExampleEntity2(0, "Bob" + systemTimeInMillis(),
            50)
        entityToInsert.uid = exampleDb2.exampleDao2().insertAsyncAndGiveId(entityToInsert)
        assertEquals(entityToInsert.name, exampleDb2.exampleDao2().findNameByUidAsync(entityToInsert.uid),
            "Select single column method returns expected value")
    }

    @Test
    fun givenRepDbCreated_whenEntityInserted_thenWillUsePkManager() = GlobalScope.promise {
        val startTime = systemTimeInMillis()
        openRepoDb()
        val repEntity = RepEntity().apply {
            this.reNumField = 42
        }
        repEntity.rePrimaryKey =  repDb.repDao.insertAsync(repEntity)
        println("Primary key = ${repEntity.rePrimaryKey}")
        val entityFromDb = repDb.repDao.findByUidAsync(repEntity.rePrimaryKey)
        assertEquals(repEntity.reNumField, entityFromDb!!.reNumField, message = "Found same field value from db")
        assertTrue(entityFromDb.rePrimaryKey > 1000)
        assertTrue(entityFromDb.reLastChangeTime > startTime, message = "Last changed time was set")

    }

    @Test
    fun givenInvalidationListenerActive_whenEntityInserted_thenWillReceiveEvent() = GlobalScope.promise {
        openRepoDb()
        val repEntity = RepEntity().apply {
            this.reNumField = 42
        }

        val completableDeferred = CompletableDeferred<Boolean>()
        repDb.addInvalidationListener(ChangeListenerRequest(listOf("RepEntity")) {
            if("RepEntity" in it)
                completableDeferred.complete(true)
        })

        repEntity.rePrimaryKey = repDb.repDao.insertAsync(repEntity)
        withTimeout(5000) {
            completableDeferred.await()
        }
        assertTrue(completableDeferred.await(), "RepEntity change triggered")
    }
}