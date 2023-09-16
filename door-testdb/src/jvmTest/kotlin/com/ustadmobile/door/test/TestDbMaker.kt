package com.ustadmobile.door.test

import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.door.DoorDatabaseCallbackStatementList
import com.ustadmobile.door.DoorSqlDatabase
import db2.ExampleDatabase2

actual fun makeInMemoryTestDb(nodeId: Long) {

}

actual suspend fun makeExample2Database(nodeId: Long): ExampleDatabase2 {
    return DatabaseBuilder.databaseBuilder( ExampleDatabase2::class, "jdbc:sqlite::memory:",nodeId)
        .addCallback(object: DoorDatabaseCallbackStatementList {
            override fun onOpen(db: DoorSqlDatabase): List<String> {
                return listOf()
            }

            override fun onCreate(db: DoorSqlDatabase): List<String> {
                return listOf("UPDATE ExampleEntity2 SET someNumber = someNumber + 1")
            }
        })
        .build()
}