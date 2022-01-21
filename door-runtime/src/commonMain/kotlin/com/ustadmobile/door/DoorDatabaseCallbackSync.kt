package com.ustadmobile.door

interface DoorDatabaseCallbackSync : DoorDatabaseCallback{

    fun onCreate(db: DoorSqlDatabase)

    fun onOpen(db: DoorSqlDatabase)

}