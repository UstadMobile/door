package com.ustadmobile.door.test

import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.door.DoorDatabaseCallbackStatementList
import com.ustadmobile.door.DoorSqlDatabase
import db2.ExampleDatabase2
import db3.ExampleDb3

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

actual suspend fun makeExample3Database(nodeId: Long): ExampleDb3 {
    return DatabaseBuilder.databaseBuilder(ExampleDb3::class, "jdbc:sqlite::memory:", nodeId)
        .build()
}
