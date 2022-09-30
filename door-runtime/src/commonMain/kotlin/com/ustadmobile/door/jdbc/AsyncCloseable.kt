package com.ustadmobile.door.jdbc

interface AsyncCloseable {

    suspend fun closeAsync()

}