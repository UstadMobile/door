package com.ustadmobile.door

import java.sql.*

abstract class EntityInsertionAdapter<T>(dbType: Int) {

    abstract fun bindPreparedStmtToEntity(stmt: PreparedStatement, entity: T)

    abstract fun makeSql(returnsId: Boolean): String

    fun insert(entity: T, con: Connection) {
        var stmt = null as PreparedStatement?
        try {
            stmt = con.prepareStatement(makeSql(false))
            bindPreparedStmtToEntity(stmt, entity)
            stmt.executeUpdate()
        }finally {
            stmt?.close()
            con.autoCommit = false
            con.close()
        }
    }

    private fun getGeneratedKey(stmt: Statement): Long {
        var generatedKeyRs = null as ResultSet?
        var generatedKey = 0L
        try {
            generatedKeyRs = stmt.generatedKeys
            if(generatedKeyRs.next())
                generatedKey = generatedKeyRs.getLong(1)
        }finally {
            generatedKeyRs?.close()
        }

        return generatedKey
    }

    fun insertAndReturnId(entity: T, con: Connection): Long {
        var stmt = null as PreparedStatement?
        var generatedKey = 0L
        try {
            stmt = con.prepareStatement(makeSql(true), Statement.RETURN_GENERATED_KEYS)
            bindPreparedStmtToEntity(stmt, entity)
            stmt.executeUpdate()
            generatedKey = getGeneratedKey(stmt)
        }finally {
            stmt?.close()
            con.close()
        }

        return generatedKey
    }

    fun insertListAndReturnIds(entities: List<T>, con :Connection): List<Long> {
        var stmt = null as PreparedStatement?
        val generatedKeys = mutableListOf<Long>()
        try {
            con.autoCommit = false
            stmt = con.prepareStatement(makeSql(true), Statement.RETURN_GENERATED_KEYS)
            for(entity in entities) {
                bindPreparedStmtToEntity(stmt, entity)
                stmt.executeUpdate()
                generatedKeys.add(getGeneratedKey(stmt))
            }
        }catch(e: SQLException) {
            e.printStackTrace()
        }finally {
            con.autoCommit = true
            stmt?.close()
            con.close()
        }

        return generatedKeys
    }

    fun insertList(entities: List<T>, con: Connection) {
        var stmt = null as PreparedStatement?
        try {
            con.autoCommit = false
            stmt = con.prepareStatement(makeSql(false))
            for(entity in entities) {
                bindPreparedStmtToEntity(stmt, entity)
                stmt.executeUpdate()
            }
            con.commit()
        }catch(e: SQLException) {
            e.printStackTrace()
        }finally {
            stmt?.close()
            con.autoCommit = true
            con.close()
        }
    }


}

