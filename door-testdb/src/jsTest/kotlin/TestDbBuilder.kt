import com.ustadmobile.door.DatabaseBuilder
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import kotlin.test.*

class TestDbBuilder {

    lateinit var databaseJs: ExampleDatabaseJs

    @BeforeTest
    fun setup(){
        DatabaseBuilder.registerImplementation<ExampleDatabaseJs>(ExampleDatabaseJs::class, ExampleDatabaseJs_Impl::class)
        databaseJs =  DatabaseBuilder.databaseBuilder(Any(), ExampleDatabaseJs::class, "jsDb")
            .webWorker("./worker.sql-wasm.js")
            .build()
        databaseJs.clearAllTables()
    }

    @Test
    fun givenInsertedEntry_whenQueried_shouldBeFound() = GlobalScope.promise {
        val dao = databaseJs.exampleJsDao()
        val entryId = 10L
        /*dao.insertAsync(ExampleJsEntity().apply {
            uid = entryId
            name = "SampleEntityName"
        })
        val entity = dao.findByUid(entryId)*/
    }
}