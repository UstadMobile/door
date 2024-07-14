package com.ustadmobile.door.http

import com.ustadmobile.door.room.RoomDatabase

interface DoorJsonRequest {

    /**
     * This should be the wrapped database
     */
    val db: RoomDatabase

    fun queryParam(paramName: String): String?

    fun requireParam(paramName: String): String

    suspend fun requireBodyAsString(): String

    suspend fun bodyAsStringOrNull(): String?

    fun requireHeader(header: String): String

    fun requireNodeId(): Long


}