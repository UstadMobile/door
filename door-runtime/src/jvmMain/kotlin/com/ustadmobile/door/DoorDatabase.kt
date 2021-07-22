package com.ustadmobile.door


import com.ustadmobile.door.jdbc.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.regex.Pattern

@Suppress("unused") //Some functions are used by generated code
actual abstract class DoorDatabase actual constructor() : DoorDatabaseCommon(){

    override var jdbcDbType: Int = -1
        get() = sourceDatabase?.jdbcDbType ?: field
        protected set

    @Volatile
    override var jdbcArraySupported: Boolean = false
        get() = sourceDatabase?.jdbcArraySupported ?: field
        protected set

    protected fun setupFromDataSource() {
        openConnection().use { dbConnection ->
            jdbcDbType = DoorDbType.typeIntFromProductName(dbConnection.metaData?.databaseProductName ?: "")
            jdbcArraySupported = jdbcDbType == DoorDbType.POSTGRES
        }
    }


    actual override fun runInTransaction(runnable: Runnable) {
        super.runInTransaction(runnable)
    }

    actual abstract fun clearAllTables()

}