package com.ustadmobile.door.jdbc

import kotlin.Array

actual interface DatabaseMetadata {

    actual fun getTables(
        catalog: String?,
        schemaPattern: String?,
        tableNamePattern: String?,
        types: Array<out String>
    ): ResultSet

    //On JS all queries must be async!
    suspend fun getTablesAsync(
        catalog: String?,
        schemaPattern: String?,
        tableNamePattern: String?,
        types: Array<out String>
    ): ResultSet

    actual fun getDatabaseProductName(): String
}