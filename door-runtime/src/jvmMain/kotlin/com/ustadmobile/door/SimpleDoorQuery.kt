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
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun bindToPreparedStmt(stmt: PreparedStatement, db: DoorDatabase, con: Connection) {
        val paramsToBind = values
        if(paramsToBind != null) {
            var paramIndex = 1
            for(param in paramsToBind) {
                if(param is List<*> || (param?.javaClass?.isArray ?: false)) {
                    val paramType = if(param is List<*> && param.isNotEmpty()) {
                        SQL_COMPONENT_TYPE_MAP.get(param[0]!!::class)
                    }else if(param is Array<*> && param.isNotEmpty()) {
                        SQL_COMPONENT_TYPE_MAP.get(param[0]!!::class)
                    }else {
                        "TEXT"
                    }

                    val valuesArr = if(param is List<*>) {
                        param.toTypedArray()
                    }else if(param is Array<*>) {
                        param
                    }else {
                        throw IllegalArgumentException("Array param is not a list or array")
                    }


                    val arrayParam = if(db.arraySupported) {
                        con.createArrayOf(paramType, valuesArr)
                    }else {
                        PreparedStatementArrayProxy.createArrayOf(paramType!!, valuesArr)
                    } as java.sql.Array

                    stmt.setArray(paramIndex++, arrayParam)
                }else {
                    stmt.setObject(paramIndex++, param)
                }

            }
        }
    }

}