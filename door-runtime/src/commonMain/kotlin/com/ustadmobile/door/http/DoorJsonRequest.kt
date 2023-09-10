package com.ustadmobile.door.http

import com.ustadmobile.door.room.RoomDatabase

interface DoorJsonRequest {

    /**
     * This should be the wrapped database
     */
    val db: RoomDatabase

    fun requireParam(paramName: String): String

    suspend fun requireBodyAsString(): String

    fun requireHeader(header: String): String

    fun requireNodeId(): Long


}