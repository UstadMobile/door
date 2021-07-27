package com.ustadmobile.door.jdbc

import kotlin.Array

actual interface DatabaseMetadata {

    val databaseProductName: String

    actual fun getTables(
        catalog: String?,
        schemaPattern: String?,
        tableNamePattern: String?,
        types: Array<out String>
    ): ResultSet
}