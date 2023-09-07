package com.ustadmobile.door.http

interface DoorJsonRequest {

    fun requireIntParam(paramName: String): Int

    fun requireLongParam(paramName: String): Long

    fun requireStringParam(paramName: String): String

    fun requireBodyAsString(): String

}