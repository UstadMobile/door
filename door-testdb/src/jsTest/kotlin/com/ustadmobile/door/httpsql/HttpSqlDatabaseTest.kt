package com.ustadmobile.door.httpsql

import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.door.DatabaseBuilderOptionsHttpSql
import com.ustadmobile.door.DatabaseBuilderOptionsSqliteJs
import com.ustadmobile.door.lifecycle.Observer
import com.ustadmobile.door.room.InvalidationTrackerObserver
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import io.ktor.client.*
import io.ktor.client.engine.js.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import repdb.RepDb
import repdb.RepDbJsImplementations
import repdb.RepEntity
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class HttpSqlDatabaseTest {

    private lateinit var httpClient: HttpClient

    private lateinit var json: Json

    @BeforeTest
    fun setup() {
        Napier.base(DebugAntilog())
        json = Json { encodeDefaults = true }
        httpClient = HttpClient(Js) {
            install(ContentNegotiation) {
                json()
            }
        }
    }

    suspend fun openDb() : RepDb {
        return DatabaseBuilder.databaseBuilder(DatabaseBuilderOptionsHttpSql(RepDb::class,
            RepDbJsImplementations, "httpsql://localhost:8098/httpsql/", 30, httpClient, json))
            .build()
    }

    @Test
    fun givenValidDbUrl_whenDatabaseEntityInserted_thenShouldBeRetrievable() = GlobalScope.promise {
        val db = openDb()
        val insertedEntity = RepEntity().apply {
            reNumField = 142
            rePrimaryKey = db.repDao.insertAsync(this)
        }

        val retrievedEntity = db.repDao.findByUidAsync(insertedEntity.rePrimaryKey)
        console.log("Inserted PK: ${retrievedEntity?.rePrimaryKey}")
        assertEquals(insertedEntity.reNumField, retrievedEntity?.reNumField ?: 0,
            "Entity inserted has same value as entity retrieved")
        delay(1000)
    }

    @Test
    fun givenListentingForInvalidations_whenEntityUpdated_thenReceiveInvalidationEvent() = GlobalScope.promise {
        val db = openDb()

        val numFieldValue1 = 36
        val numFieldValue2 = 42
        val insertedEntity = RepEntity().apply {
            reNumField = numFieldValue1
            rePrimaryKey = db.repDao.insertAsync(this)
        }

        val value2CompleteableDeferred = CompletableDeferred<Boolean>()

        val originalValueDeferred = CompletableDeferred<Boolean>()

        val observer = Observer<RepEntity?> {
            if(it?.reNumField == numFieldValue1)
                originalValueDeferred.complete(true)
            else if(it?.reNumField == numFieldValue2) {
                value2CompleteableDeferred.complete(true)
            }
        }

        val liveData = db.repDao.findByUidLive(insertedEntity.rePrimaryKey)
        liveData.observeForever(observer)

        withTimeout(5000) { originalValueDeferred.await() }

        db.repDao.updateAsync(insertedEntity.apply {
            reNumField = numFieldValue2
        })

        withTimeout(5001) { value2CompleteableDeferred.await() }

        liveData.removeObserver(observer)
    }


}