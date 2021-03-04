package com.ustadmobile.door

expect interface DoorSqlDatabase {

    fun execSQL(sql: String)
}