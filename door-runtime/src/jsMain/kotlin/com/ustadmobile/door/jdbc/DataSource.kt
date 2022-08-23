package com.ustadmobile.door.jdbc

actual interface DataSource {

    actual fun getConnection(): Connection

}