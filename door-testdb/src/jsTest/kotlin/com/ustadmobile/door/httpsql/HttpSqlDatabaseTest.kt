package com.ustadmobile.door.httpsql

import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.door.DatabaseBuilderOptionsHttpSql
import com.ustadmobile.door.DatabaseBuilderOptionsSqliteJs
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import io.ktor.client.*
import io.ktor.client.engine.js.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
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

    lateinit var repDb: RepDb

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
        assertEquals(insertedEntity.reNumField, retrievedEntity?.reNumField ?: 0,
            "Entity inserted has same value as entity retrieved")
    }

}