import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import wrappers.SQLiteDatasourceJs
import kotlin.js.json

//abstract class ExampleDatabaseJs_Impl(override val datasource: SQLiteDatasourceJs): ExampleDatabaseJs(datasource) {
//
//    override fun exampleJsDao(): ExampleDaoJs {
//        return ExampleDaoJs(this)
//    }
//
//    override suspend fun createAllTables() {
//        super.createAllTables()
//        executeQuery("CREATE TABLE IF NOT EXISTS ExampleJsEntity(uid INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL , name TEXT);")
//    }
//
//    override fun clearAllTables() {
//        GlobalScope.launch {
//            executeQuery("DROP TABLE IF EXISTS ExampleJsEntity")
//        }
//    }
//
//    private suspend fun executeQuery(sqlQuery: String){
//        datasource.sendMessage(json(
//            "action" to "exec",
//            "sql" to sqlQuery))
//    }
//}