package com.ustadmobile.door

import com.ustadmobile.door.room.RoomDatabase
import com.ustadmobile.door.ext.arraySupported
import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.door.jdbc.PreparedStatement
import com.ustadmobile.door.ext.isArray
import kotlin.reflect.KClass

actual class SimpleDoorQuery actual constructor(
    override val sql: String,
    override val values: Array<out Any?>?
) : DoorQuery {

    override val argCount: Int
        get() = TODO("Not yet implemented")


    //Cast is here to be clear about jdbc array vs. normal array
    @Suppress("USELESS_CAST")
    override fun bindToPreparedStmt(stmt: PreparedStatement, db: RoomDatabase) {
        val paramsToBind = values
        val connection = stmt.getConnection()
        if(paramsToBind != null) {
            var paramIndex = 1
            for(param in paramsToBind) {
                if(param is List<*> || (param?.isArray() == true)) {
                    val paramType = when {
                        param is List<*> && param.isNotEmpty() -> requireSqlType(param.first())
                        param is Array<*> && param.isNotEmpty() -> requireSqlType(param.first())
                        else -> "TEXT"
                    }

                    val valuesArr = if(param is List<*>) {
                        param.toTypedArray()
                    }else if(param is Array<*>) {
                        param
                    }else {
                        throw IllegalArgumentException("Array param is not a list or array")
                    }


                    val arrayParam = if(db.arraySupported) {
                        connection.createArrayOf(paramType, valuesArr)
                    }else {
                        JdbcArrayProxy(paramType, valuesArr)
                    } as com.ustadmobile.door.jdbc.Array

                    stmt.setArray(paramIndex++, arrayParam)
                }else {
                    stmt.setObject(paramIndex++, param)
                }

            }
        }
    }

    companion object {


        private val SQL_COMPONENT_TYPE_MAP : Map<out KClass<*>, String> = mapOf(Long::class to "BIGINT",
            Int::class to "INTEGER",
            Short::class to "SMALLINT",
            Boolean::class to "BOOLEAN",
            Float::class to "FLOAT",
            Double::class to "DOUBLE",
            String::class to "TEXT")

        fun requireSqlType(any: Any?): String {
            val anyObj = any ?: throw IllegalArgumentException("requireSqlType null")
            return SQL_COMPONENT_TYPE_MAP[anyObj::class] ?: throw IllegalArgumentException("Unsupported: ${any::class}")
        }
    }

}