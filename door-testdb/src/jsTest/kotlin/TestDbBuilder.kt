import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.door.DatabaseBuilderOptions
import com.ustadmobile.door.util.systemTimeInMillis
import db2.ExampleDatabase2
import db2.ExampleDatabase2_JdbcKt
import db2.ExampleEntity2
import kotlinx.browser.window
import kotlinx.coroutines.*
import org.w3c.dom.Worker
import org.w3c.dom.url.URL
import org.w3c.files.Blob
import kotlin.js.Promise
import kotlin.test.*

class TestDbBuilder {
     lateinit var exampleDb2: ExampleDatabase2

     //We can not use @BeforeTest here, it is async call test will run before it finishes setting up.
     private suspend fun openClearDb() {
        val res = (window.fetch("https://endlessfame.co.tz/worker.sql-asm.js") as Promise<dynamic>).await()
        val data = (res.blob() as Promise<dynamic>).await()
        val workerBlobUrl = URL.createObjectURL(data as Blob)
        val builderOptions = DatabaseBuilderOptions(
            ExampleDatabase2::class,
            ExampleDatabase2_JdbcKt::class, "jsDb1",workerBlobUrl)
        exampleDb2 = DatabaseBuilder.databaseBuilder<ExampleDatabase2>(builderOptions).build()
        //exampleDb2?.clearAllTables()
    }

    @Test
    fun givenDbShouldOpen() = GlobalScope.promise {
        openClearDb()
        val exampleDao2 = exampleDb2.exampleDao2()
        assertNotNull(exampleDao2)
        val exList = listOf(ExampleEntity2(0, "bob",42))
        exampleDao2.insertListAsync(exList)
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
        val entityToInsert = ExampleEntity2(0, "Bob" + systemTimeInMillis(),
            50)
        entityToInsert.uid = exampleDb2.exampleDao2().insertAsyncAndGiveId(entityToInsert)
        assertEquals("++${entityToInsert.name}", exampleDb2.exampleDao2().findNameByUidAsync(entityToInsert.uid),
            "Select single column method returns expected value")
    }
}