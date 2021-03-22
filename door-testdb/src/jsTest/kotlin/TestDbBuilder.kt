import com.ustadmobile.door.DatabaseBuilder
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import kotlin.test.*

class TestDbBuilder {

    lateinit var databaseJs: ExampleDatabaseJs

    @BeforeTest
    fun initializeAll(){
        DatabaseBuilder.registerImplementation<ExampleDatabaseJs>(ExampleDatabaseJs::class, ExampleDatabaseJs_Impl::class)
        databaseJs =  DatabaseBuilder.databaseBuilder(Any(), ExampleDatabaseJs::class, "jsDb")
            .webWorker("worker.sql-wasm.js")
            .build()
        databaseJs.clearAllTables()
    }

    @Test
    fun givenDb_whenInitialized_shouldNotBeNull() = GlobalScope.promise{
        val completed = databaseJs.initCompletable.await()
        assertTrue(completed, "Database is was initialized")
        assertNotNull(databaseJs, "Database instance is not null")
    }

    @Test
    fun givenInsertedEntry_whenQueried_shouldBeFound() = GlobalScope.promise {
        val dao = databaseJs.exampleJsDao()
        dao.insertAsync(ExampleJsEntity().apply {
            uid = 10
            name = "SampleEntityName"
        })
        val entity = dao.findByUid(10)
        assertEquals(0,entity?.uid)
    }
}