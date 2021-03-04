package com.ustadmobile.door

interface DoorDatabaseCallback {

    fun onCreate(db: DoorSqlDatabase)

    fun onOpen(db: DoorSqlDatabase)

}