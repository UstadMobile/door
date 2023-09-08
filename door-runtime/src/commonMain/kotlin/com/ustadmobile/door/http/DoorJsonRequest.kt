package com.ustadmobile.door.http

import com.ustadmobile.door.room.RoomDatabase

interface DoorJsonRequest {

    /**
     * This should be the wrapped database
     */
    val db: RoomDatabase

    fun requireIntParam(paramName: String): Int

    fun requireLongParam(paramName: String): Long

    fun requireStringParam(paramName: String): String

    fun requireBodyAsString(): String

    fun requireHeader(header: String): String

    fun requireNodeId(): Long


}