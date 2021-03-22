import db2.ExampleDatabase2
import wrappers.SQLiteDatasourceJs

abstract class ExampleDatabaseJs(val datasource: SQLiteDatasourceJs): ExampleDatabase2() {
    abstract fun exampleJsDao(): ExampleDaoJs
}