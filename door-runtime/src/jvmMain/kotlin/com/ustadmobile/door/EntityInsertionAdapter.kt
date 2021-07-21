package com.ustadmobile.door

import java.sql.*

/**
 * This is similar to the EntityInsertionAdapter on Room. It is used by generated code.
 * @param dbType The DbType constant as per DoorDbType
 */
@Suppress("unused", "VARIABLE_WITH_REDUNDANT_INITIALIZER") //What appears unused to the IDE is actually used by generated code
abstract class EntityInsertionAdapter<T>(protected val dbType: Int) {

    /**
     * Set values on the PreparedStatement (which is created using makeSql) for the given entity. This is implemented
     * by generated code on the JDBC DAO.
     *
     * @param stmt The PreparedStatement that was prepared using the output of makeSql
     * @param entity the entity to set values from
     */
    abstract fun bindPreparedStmtToEntity(stmt: PreparedStatement, entity: T)

    /**
     * Generate the SQL to insert this into the database. This is implemented by generated code.
     *
     * @param returnsId If true, it indicates that the SQL should support returning primary keys
     * @return SQL as per the dbType
     */
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

