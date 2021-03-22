import wrappers.SQLiteDatasourceJs

abstract class ExampleDatabaseJs_Impl(override val datasource: SQLiteDatasourceJs): ExampleDatabaseJs(datasource) {

    override fun exampleJsDao(): ExampleDaoJs {
        return ExampleDaoJs(this)
    }

    override suspend fun createAllTables() {
        super.createAllTables()
    }

    override fun clearAllTables() {}
}