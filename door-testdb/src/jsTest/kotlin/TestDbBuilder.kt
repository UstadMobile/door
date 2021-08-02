import com.ustadmobile.door.DatabaseBuilderOptions
import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.door.DoorDbType
import db2.ExampleDatabase2
import db2.ExampleDatabase2_JdbcKt
import db2.ExampleEntity2
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import wrappers.DatabaseExportToIndexedDbCallback
import wrappers.SQLiteDatasourceJs
import kotlin.test.*

class TestDbBuilder {

    private suspend fun setupDatabase() : ExampleDatabase2 {
        val builderOptions = DatabaseBuilderOptions(ExampleDatabase2::class,
            ExampleDatabase2_JdbcKt::class, "jsDb1")
        val exportCallback = object: DatabaseExportToIndexedDbCallback{
            override fun onExport(datasource: SQLiteDatasourceJs) {}
        }
        val databaseJs =  DatabaseBuilder.databaseBuilder<ExampleDatabase2>(builderOptions, exportCallback)
            .webWorker("./worker.sql-asm.js")
            .build()

        //Not implemented yet
        //databaseJs.clearAllTables()
        return databaseJs
    }

    @Test
    fun givenDatabaseBuilder_whenBuilding_shouldBuildAndInitializeTheDatabase() = GlobalScope.promise{
        val db = setupDatabase()
        assertEquals(db.jdbcDbType, DoorDbType.SQLITE)
    }

    @Test
    fun givenInsertedEntry_whenQueried_shouldBeEqual() = GlobalScope.promise {
        val entity = ExampleEntity2().apply {
            uid = 10L
            someNumber = 238L
            name = "SampleEntityName"
        }
        val db = setupDatabase()
        val dao = db.exampleDao2()
        dao.insertAsync(entity)
        val retrievedEntity = dao.findByUidAsync(entity.uid)
        assertEquals(entity, retrievedEntity, "Entities are equal")
    }
}