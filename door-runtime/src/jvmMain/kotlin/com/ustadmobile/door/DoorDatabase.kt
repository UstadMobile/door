package com.ustadmobile.door


import com.ustadmobile.door.jdbc.*

@Suppress("unused") //Some functions are used by generated code
actual abstract class DoorDatabase actual constructor() : DoorDatabaseCommon(){

    override var jdbcDbType: Int = -1
        get() = sourceDatabase?.jdbcDbType ?: field
        protected set

    @Volatile
    override var jdbcArraySupported: Boolean = false
        get() = sourceDatabase?.jdbcArraySupported ?: field
        protected set

    override val tableNames: List<String> by lazy {
        val delegatedDatabaseVal = sourceDatabase
        if(delegatedDatabaseVal != null) {
            delegatedDatabaseVal.tableNames
        }else {
            var con = null as Connection?
            val tableNamesList = mutableListOf<String>()
            var tableResult = null as ResultSet?
            try {
                con = openConnection()
                val metadata = con.getMetaData()
                tableResult = metadata.getTables(null, null, "%", arrayOf("TABLE"))
                while(tableResult.next()) {
                    tableResult.getString("TABLE_NAME")?.also {
                        tableNamesList.add(it)
                    }
                }
            }finally {
                con?.close()
            }

            tableNamesList.toList()
        }

    }

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