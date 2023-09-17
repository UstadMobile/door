package com.ustadmobile.door.test

import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.door.DatabaseBuilderOptions
import db2.ExampleDatabase2
import db2.ExampleDatabase2JsImplementations

actual fun makeInMemoryTestDb(nodeId: Long){

}

actual suspend fun makeExample2Database(nodeId: Long): ExampleDatabase2 {
    return DatabaseBuilder.databaseBuilder(
        DatabaseBuilderOptions(ExampleDatabase2::class, ExampleDatabase2JsImplementations,
            webWorkerPath = "worker.sql-wasm.js",
            dbUrl = "sqlite::memory:",
            nodeId = nodeId)
    ).build()
}