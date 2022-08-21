package com.ustadmobile.door.jdbc

expect interface DatabaseMetadata {

    fun getTables(catalog: String?, schemaPattern: String?, tableNamePattern: String?, types: kotlin.Array<out String>): ResultSet

    fun getDatabaseProductName(): String

}