import db2.ExampleDatabase2

abstract class ExampleDatabaseJs: ExampleDatabase2() {

    override suspend fun createAllTables() {
        super.createAllTables()
    }
}