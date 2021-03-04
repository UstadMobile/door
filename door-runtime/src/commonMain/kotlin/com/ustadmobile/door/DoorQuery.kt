package com.ustadmobile.door

expect interface DoorQuery {

    fun getSql(): String

    fun getArgCount(): Int

}