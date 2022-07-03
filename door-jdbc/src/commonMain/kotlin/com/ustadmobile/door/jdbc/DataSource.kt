package com.ustadmobile.door.jdbc

expect interface DataSource {

    fun getConnection(): Connection

}