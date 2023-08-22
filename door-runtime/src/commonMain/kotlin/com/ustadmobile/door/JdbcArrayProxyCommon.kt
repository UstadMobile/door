package com.ustadmobile.door

import com.ustadmobile.door.jdbc.Array
import com.ustadmobile.door.jdbc.TypesKmp

abstract class JdbcArrayProxyCommon(
    val typeName: String,
    val objects: kotlin.Array<out Any?>
) : Array{

    private val _baseType: Int

    init {

        when (typeName) {
            "INTEGER" -> _baseType = TypesKmp.INTEGER
            "VARCHAR" -> _baseType = TypesKmp.VARCHAR
            "BIGINT" -> _baseType = TypesKmp.BIGINT
            "SHORT" -> _baseType = TypesKmp.SMALLINT
            "BOOLEAN" -> _baseType = TypesKmp.BOOLEAN
            "TEXT" -> _baseType = TypesKmp.LONGVARCHAR
            else -> throw IllegalStateException("JdbcArrayProxyCommon: Unsupported type: $typeName")
        }
    }

    override fun getBaseTypeName(): String {
        return typeName
    }

    override fun getBaseType(): Int {
        return _baseType
    }

    override fun getArray(): Any {
        return this
    }

}