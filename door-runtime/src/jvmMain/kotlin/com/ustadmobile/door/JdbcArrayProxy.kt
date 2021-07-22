package com.ustadmobile.door

import java.sql.*

internal actual class JdbcArrayProxy actual constructor(
    typeName: String,
    objects: kotlin.Array<out Any?>
) : JdbcArrayProxyCommon(typeName, objects){

    override fun getArray(map: Map<String, Class<*>>): Any? {
        return null
    }

    override fun getArray(l: Long, i: Int): Any? {
        return null
    }

    override fun getArray(l: Long, i: Int, map: Map<String, Class<*>>): Any? {
        return null
    }

    override fun getResultSet(): ResultSet? {
        return null
    }

    override fun getResultSet(map: Map<String, Class<*>>): ResultSet? {
        return null
    }

    override fun getResultSet(l: Long, i: Int): ResultSet? {
        return null
    }

    override fun getResultSet(l: Long, i: Int, map: Map<String, Class<*>>): ResultSet? {
        return null
    }

    override fun free() {

    }
}