import com.ustadmobile.door.DatabaseBuilder
import db2.ExampleDatabase2
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DbTestClass {

    @BeforeTest
    fun inits(){
        DatabaseBuilder.registerImplementation<ExampleDatabaseJs>(ExampleDatabaseJs::class, ExampleDatabaseJs::class)
    }

    @Test
    fun tests(){
        val databaseJs = DatabaseBuilder.databaseBuilder(Any(), ExampleDatabaseJs::class, "JsDb").build()
        assertNull(databaseJs)
    }
}