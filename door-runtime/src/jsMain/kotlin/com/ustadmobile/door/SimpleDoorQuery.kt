package com.ustadmobile.door

import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.door.jdbc.PreparedStatement

actual class SimpleDoorQuery actual constructor(private val sql: String, override val values: Array<out Any?>?) : DoorQuery {

    val SQL_COMPONENT_TYPE_MAP = mapOf(Long::class to "BIGINT",
        Int::class to "INTEGER",
        Short::class to "SMALLINT",
        Boolean::class to "BOOLEAN",
        Float::class to "FLOAT",
        Double::class to "DOUBLE",
        String::class to "TEXT")

    override fun getSql() = sql

    override fun getArgCount(): Int {
        TODO("getArgCount: Not yet implemented")
    }


    override fun bindToPreparedStmt(stmt: PreparedStatement, db: DoorDatabase, con: Connection) {
        val paramsToBind = values
        if(paramsToBind != null) {
            var paramIndex = 1
            for(param in paramsToBind) {
                if(param is List<*> || param is Array<*>) {
                    val paramType = if(param is List<*> && param.isNotEmpty()) {
                        SQL_COMPONENT_TYPE_MAP[param[0]!!::class]
                    }else if(param is Array<*> && param.isNotEmpty()) {
                        SQL_COMPONENT_TYPE_MAP[param[0]!!::class]
                    }else {
                        "TEXT"
                    }

                    val valuesArr = when (param) {
                        is List<*> -> param.toTypedArray()
                        is Array<*> -> param
                        else -> throw IllegalArgumentException("Array param is not a list or array")
                    }

                    val arrayParam = if(db.arraySupported) {
                        paramType?.let { con.createArrayOf(it, valuesArr) }
                    }else {
                        JdbcArrayProxy(paramType ?: throw IllegalStateException("ParamType is null!"), valuesArr)
                    }

                    if (arrayParam != null) {
                        stmt.setArray(paramIndex++, arrayParam)
                    }
                }else {
                    stmt.setObject(paramIndex++, param)
                }

            }
        }
    }


}