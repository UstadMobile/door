import db2.ExampleDatabase2
import wrappers.SQLiteDatasourceJs

abstract class ExampleDatabaseJs(open val datasource: SQLiteDatasourceJs): ExampleDatabase2() {
    abstract fun exampleJsDao(): ExampleDaoJs
}