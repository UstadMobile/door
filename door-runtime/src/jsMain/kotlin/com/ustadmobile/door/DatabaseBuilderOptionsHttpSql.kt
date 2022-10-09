package com.ustadmobile.door

import com.ustadmobile.door.room.RoomDatabase
import com.ustadmobile.door.util.DoorJsImplClasses
import io.ktor.client.*
import kotlinx.serialization.json.Json
import kotlin.reflect.KClass

class DatabaseBuilderOptionsHttpSql<T: RoomDatabase>(
    dbClass: KClass<T>,
    dbImplClasses: DoorJsImplClasses<T>,
    dbUrl: String,
    jdbcQueryTimeout: Int = 10,
    val httpClient: HttpClient,
    val json: Json,
): DatabaseBuilderOptions<T>(
    dbClass, dbImplClasses, dbUrl, jdbcQueryTimeout, attachmentStorage = null,
)