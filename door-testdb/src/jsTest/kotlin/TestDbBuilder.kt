import com.ustadmobile.door.DatabaseBuilder
import kotlin.test.BeforeTest
import kotlin.test.Test

class TestDbBuilder {

    lateinit var doorDb: ExampleDatabaseJs

    @BeforeTest
    fun initDb(){
        DatabaseBuilder.registerImplementation<ExampleDatabaseJs>(ExampleDatabaseJs::class,ExampleDatabaseJs::class)
    }

    @Test
    fun checkBuild() {
        doorDb = DatabaseBuilder.databaseBuilder(Any(), ExampleDatabaseJs::class, "db1").build()
    }
}