package com.ustadmobile.door

interface DoorDatabaseCallbackStatementList : DoorDatabaseCallback {

    fun onCreate(db: DoorSqlDatabase): List<String>

    fun onOpen(db: DoorSqlDatabase): List<String>

}