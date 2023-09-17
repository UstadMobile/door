package com.ustadmobile.door.test

import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.door.DatabaseBuilderOptions
import db2.ExampleDatabase2
import db2.ExampleDatabase2JsImplementations
import db3.ExampleDb3
import db3.ExampleDb3JsImplementations

actual suspend fun makeExample2Database(nodeId: Long): ExampleDatabase2 {
    return DatabaseBuilder.databaseBuilder(
        DatabaseBuilderOptions(ExampleDatabase2::class, ExampleDatabase2JsImplementations,
            webWorkerPath = "worker.sql-wasm.js",
            dbUrl = "sqlite::memory:",
            nodeId = nodeId)
    ).build()
}

actual suspend fun makeExample3Database(nodeId: Long): ExampleDb3 {
    return DatabaseBuilder.databaseBuilder(
        DatabaseBuilderOptions(ExampleDb3::class, ExampleDb3JsImplementations,
            webWorkerPath = "worker.sql-wasm.js",
            dbUrl = "sqlite::memory:",
            nodeId = nodeId)
    ).build()
}
