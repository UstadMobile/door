package com.ustadmobile.door

actual class SimpleDoorQuery actual constructor(private val sql: String, values: Array<out Any?>?) :
    DoorQuery {
    override fun getSql() = sql

    override fun getArgCount(): Int {
        TODO("Not yet implemented")
    }


}