package com.ustadmobile.door.jdbc

interface DataSourceAsync: DataSource {

    suspend fun getConnectionAsync(): Connection

}