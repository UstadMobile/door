package com.ustadmobile.door

fun interface TablesInvalidationListener {

    fun onTablesInvalidated(tableNames: List<String>)

}