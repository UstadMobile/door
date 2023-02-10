package com.ustadmobile.door

//import com.ustadmobile.door.room.InvalidationTracker
//import com.ustadmobile.door.ext.withDoorTransactionAsync
//import com.ustadmobile.door.room.InvalidationTrackerObserver
//import com.ustadmobile.door.util.systemTimeInMillis
//import db2.ExampleDatabase2
//import db2.ExampleDatabase2JsImplementations
//import db2.ExampleDatabase2_JdbcKt
//import db2.ExampleEntity2
//import io.github.aakira.napier.DebugAntilog
//import io.github.aakira.napier.Napier
//import kotlinx.browser.window
//import kotlinx.coroutines.*
//import kotlinx.serialization.json.*
//import org.w3c.dom.url.URL
//import org.w3c.files.Blob
//import repdb.RepDb
//import repdb.RepDbJsImplementations
//import repdb.RepEntity
//import kotlin.js.Promise
//import kotlin.random.Random
//import kotlin.test.*

//
// 10/Feb/2023
// Testing with Kotlin/JS, at least in legacy mode, is extremely painful and unreliable. The app-testdb project will
// have to be used until moving to JS-IR.
//
//class TestDbBuilder {
//
//     private lateinit var exampleDb2: ExampleDatabase2
//
//     private lateinit var repDb: RepDb
//
//     private var repNodeId: Long = 0L
//
//     //We can not use @BeforeTest here, it is async call test will run before it finishes the setting up.
//     private suspend fun openClearDb() {
//        val res = (window.fetch("""
//            https://raw.githubusercontent.com/UstadMobile/door/dev-js-2/app-testdb/src/main/resources/worker.sql-asm.js
//        """.trimIndent()) as Promise<dynamic>).await()
//        val data = (res.blob() as Promise<dynamic>).await()
//        val workerBlobUrl = URL.createObjectURL(data as Blob)
//        val builderOptions = DatabaseBuilderOptions(
//            ExampleDatabase2::class, ExampleDatabase2JsImplementations, "sqlite:jsDb_${systemTimeInMillis()}",workerBlobUrl)
//        exampleDb2 = DatabaseBuilder.databaseBuilder<ExampleDatabase2>(builderOptions).build().also {
//            it.clearAllTablesAsync()
//        }
//    }
//
//    private suspend fun openRepoDb() {
//        val res = (window.fetch("""
//            https://raw.githubusercontent.com/UstadMobile/door/dev-js-2/app-testdb/src/main/resources/worker.sql-asm.js
//        """.trimIndent()) as Promise<dynamic>).await()
//        val data = (res.blob() as Promise<dynamic>).await()
//        val workerBlobUrl = URL.createObjectURL(data as Blob)
//
//        repNodeId = Random.nextLong(0, Long.MAX_VALUE)
//        val builderOptions = DatabaseBuilderOptions(
//            RepDb::class, RepDbJsImplementations, "sqlite:resDb_${systemTimeInMillis()}", workerBlobUrl)
//        repDb = DatabaseBuilder.databaseBuilder(builderOptions)
//            .addCallback(SyncNodeIdCallback(repNodeId))
//            .build()
//
//    }
//
//    @BeforeTest
//    fun setup() {
//        Napier.takeLogarithm()
//        Napier.base(DebugAntilog())
//    }
//
//    /*
//    @Test
//    fun givenDbShouldOpen() = GlobalScope.promise {
//        openClearDb()
//        //delay(10000)
//        val exampleDao2 = exampleDb2.exampleDao2()
//        assertNotNull(exampleDao2)
//        val exList = listOf(ExampleEntity2(0, "bob",42))
//        exampleDao2.insertListAsync(exList)
//        println("All done")
//        assertTrue(true)
//    }
//    */
//
//    @Test
//    fun givenEntryInserted_whenQueried_shouldBeEqual() = GlobalScope.promise {
//        openClearDb()
//        val entityToInsert = ExampleEntity2(0, "Bob", 50)
//        entityToInsert.uid = exampleDb2.exampleDao2().insertAsyncAndGiveId(entityToInsert)
//
//        val entityFromQuery = exampleDb2.exampleDao2().findByUidAsync(entityToInsert.uid)
//
//        assertNotEquals(0, entityToInsert.uid)
//        assertEquals(entityToInsert, entityFromQuery,
//            "Entity retrieved from database is the same as entity inserted")
//    }
//
//    @Test
//    fun givenEntryInserted_whenSingleValueQueried_shouldBeEqual() = GlobalScope.promise{
//        openClearDb()
//        val entityToInsert = ExampleEntity2(0, "Bob" + systemTimeInMillis(),
//            50)
//        entityToInsert.uid = exampleDb2.exampleDao2().insertAsyncAndGiveId(entityToInsert)
//        assertEquals(entityToInsert.name, exampleDb2.exampleDao2().findNameByUidAsync(entityToInsert.uid),
//            "Select single column method returns expected value")
//    }
//
//    @Test
//    fun givenRepDbCreated_whenEntityInserted_thenWillUsePkManager() = GlobalScope.promise {
//        val startTime = systemTimeInMillis()
//        openRepoDb()
//        val repEntity = RepEntity().apply {
//            this.reNumField = 42
//        }
//        repEntity.rePrimaryKey =  repDb.repDao.insertAsync(repEntity)
//        val entityFromDb = repDb.repDao.findByUidAsync(repEntity.rePrimaryKey)
//        assertEquals(repEntity.reNumField, entityFromDb!!.reNumField, message = "Found same field value from db")
//        assertTrue(entityFromDb.rePrimaryKey > 1000)
//        assertTrue(entityFromDb.reLastChangeTime > startTime, message = "Last changed time was set")
//
//        assertEquals(repNodeId, repDb.repDao.selectSyncNodeId(),
//            message = "SyncNodeClientId was set by callback")
//    }
//
//    //@Test
//    fun givenInvalidationListenerActive_whenEntityInserted_thenWillReceiveEvent() = GlobalScope.promise {
//        openRepoDb()
//        val repEntity = RepEntity().apply {
//            this.reNumField = 42
//        }
//
//        val completableDeferred = CompletableDeferred<Boolean>()
//
//        repDb.invalidationTracker.addObserver(object: InvalidationTrackerObserver(arrayOf("RepEntity")) {
//            override fun onInvalidated(tables: Set<String>) {
//                if("RepEntity" in tables)
//                    completableDeferred.complete(true)
//            }
//        })
//
//        repEntity.rePrimaryKey = repDb.repDao.insertAsync(repEntity)
//        withTimeout(5000) {
//            completableDeferred.await()
//        }
//        assertTrue(completableDeferred.await(), "RepEntity change triggered")
//    }
//
//    @Test
//    fun givenRunInTransactionUsed_whenInserted_thenCanBeRetrieved() = GlobalScope.promise {
//        openRepoDb()
//
//        repDb.withDoorTransactionAsync { txDb ->
//            val repEntity = RepEntity().apply {
//                reNumField = 42
//                rePrimaryKey = txDb.repDao.insertAsync(this)
//            }
//
//            val retrieved = txDb.repDao.findByUidAsync(repEntity.rePrimaryKey)
//            assertEquals(repEntity, retrieved, "Retrieved the same as was inserted into db")
//        }
//    }
//
//    //@Test
//    fun givenNestedRunInTransactionUsed_whenInserted_thenCanBeRetrieved() = GlobalScope.promise {
//        Napier.base(DebugAntilog())
//        openRepoDb()
//        repDb.withDoorTransactionAsync { txDb1 ->
//            txDb1.withDoorTransactionAsync { txDb2 ->
//                Napier.i("==Attempting to insert on tx2\n")
//
//                val existingInDb = txDb2.repDao.findAllAsync()
//                Napier.i("\n==Existing Rep Entities = ${existingInDb.joinToString()}==\n")
//
//                val repEntity = RepEntity().apply {
//                    reNumField = 42
//                    rePrimaryKey = txDb2.repDao.insertAsync(this)
//                }
//
//                val retrieved = txDb2.repDao.findByUidAsync(repEntity.rePrimaryKey)
//                assertEquals(repEntity, retrieved, "Retrieved the same as was inserted into db")
//            }
//        }
//
//    }
//
//}