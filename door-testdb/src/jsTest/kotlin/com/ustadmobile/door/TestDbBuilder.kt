package com.ustadmobile.door

import com.ustadmobile.door.test.runTestWithRealClock
import db3.ExampleDb3
import db3.ExampleDb3JsImplementations
import db3.ExampleEntity3
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class TestDbBuilder {

    @Test
    fun testDbOpen() = runTest {
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            val builderOptions = DatabaseBuilderOptions(ExampleDb3::class,
                ExampleDb3JsImplementations,
                webWorkerPath = "worker.sql-wasm.js",
                dbUrl = "sqlite:exampledb3"
            )
            val db = DatabaseBuilder.databaseBuilder(builderOptions).build()
            println("Opened db: $db")
        }
    }

    @Test
    fun testInsertAndSelect() = runTestWithRealClock {
        val db = DatabaseBuilder.databaseBuilder(
            DatabaseBuilderOptions(
                ExampleDb3::class, ExampleDb3JsImplementations,
                webWorkerPath = "worker.sql-wasm.js",
                dbUrl = "sqlite::memory:"
            )
        ).build()

        val entityInserted = ExampleEntity3().apply {
            cardNumber = 42
            eeUid = db.exampleEntity3Dao.insertAsync(this)
        }

        val entityFromDb = db.exampleEntity3Dao.findByUid(entityInserted.eeUid)
        assertEquals(entityInserted, entityFromDb)
        println("${entityInserted == entityFromDb}")
    }


}
