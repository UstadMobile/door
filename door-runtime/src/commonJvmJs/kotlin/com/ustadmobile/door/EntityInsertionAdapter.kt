package com.ustadmobile.door

import com.ustadmobile.door.jdbc.*
import com.ustadmobile.door.jdbc.ext.executeUpdateAsyncKmp

/**
 * This is similar to the EntityInsertionAdapter on Room. It is used by generated code.
 * @param dbType The DbType constant as per DoorDbType
 */
@Suppress("unused", "VARIABLE_WITH_REDUNDANT_INITIALIZER") //What appears unused to the IDE is actually used by generated code
abstract class EntityInsertionAdapter<T>(protected val db: DoorDatabase) {

    //used by generated code
    protected val dbType: Int = db.jdbcDbType

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

    fun insert(entity: T) {
        db.prepareAndUseStatement(makeSql(false)) { stmt ->
            bindPreparedStmtToEntity(stmt, entity)
            stmt.executeUpdate()
        }
    }

    suspend fun insertAsync(entity: T) {
        db.prepareAndUseStatementAsync(makeSql(false)) { stmt ->
            bindPreparedStmtToEntity(stmt, entity)
            stmt.executeUpdateAsyncKmp()
        }
    }

    private fun getGeneratedKey(stmt: Statement): Long {
        var generatedKeyRs = null as ResultSet?
        var generatedKey = 0L
        try {
            generatedKeyRs = stmt.getGeneratedKeys()
            if(generatedKeyRs.next())
                generatedKey = generatedKeyRs.getLong(1)
        }finally {
            generatedKeyRs?.close()
        }

        return generatedKey
    }

    fun insertAndReturnId(entity: T): Long {
        val stmtConfig = PreparedStatementConfig(makeSql(true),
            generatedKeys = StatementConstantsKmp.RETURN_GENERATED_KEYS)
        return db.prepareAndUseStatement(stmtConfig){ stmt ->
            bindPreparedStmtToEntity(stmt, entity)
            stmt.executeUpdate()
            getGeneratedKey(stmt)
        }
    }

    suspend fun insertAndReturnIdAsync(entity: T): Long {
        val stmtConfig = PreparedStatementConfig(makeSql(true),
            generatedKeys = StatementConstantsKmp.RETURN_GENERATED_KEYS)
        return db.prepareAndUseStatementAsync(stmtConfig){ stmt ->
            bindPreparedStmtToEntity(stmt, entity)
            stmt.executeUpdateAsyncKmp()
            getGeneratedKey(stmt)
        }
    }

    fun insertListAndReturnIds(entities: List<T>): List<Long> {
        val stmtConfig = PreparedStatementConfig(makeSql(true),
            generatedKeys = StatementConstantsKmp.RETURN_GENERATED_KEYS)
        val generatedKeys = mutableListOf<Long>()
        db.prepareAndUseStatement(stmtConfig) { stmt ->
            stmt.getConnection().setAutoCommit(false)
            try {
                entities.forEach {
                    bindPreparedStmtToEntity(stmt, it)
                    stmt.executeUpdate()
                    generatedKeys += getGeneratedKey(stmt)
                }
                stmt.getConnection().commit()
            }finally {
                stmt.getConnection().setAutoCommit(true)
            }
        }

        return generatedKeys.toList()
    }

    suspend fun insertListAndReturnIdsAsync(entities: List<T>): List<Long> {
        val stmtConfig = PreparedStatementConfig(makeSql(true),
            generatedKeys = StatementConstantsKmp.RETURN_GENERATED_KEYS)

        val generatedKeys = mutableListOf<Long>()
        db.prepareAndUseStatementAsync(stmtConfig) { stmt ->
            stmt.getConnection().setAutoCommit(false)
            try {
                entities.forEach {
                    bindPreparedStmtToEntity(stmt, it)
                    stmt.executeUpdateAsyncKmp()
                    generatedKeys += getGeneratedKey(stmt)
                }
                stmt.getConnection().commit()
            }finally {
                stmt.getConnection().setAutoCommit(true)
            }
        }

        return generatedKeys.toList()
    }


    fun insertList(entities: List<T>) {
        db.prepareAndUseStatement(makeSql(false)) { stmt ->
            try {
                stmt.getConnection().setAutoCommit(false)
                entities.forEach {
                    bindPreparedStmtToEntity(stmt, it)
                    stmt.executeUpdate()
                }
                stmt.getConnection().commit()
            }finally {
                stmt.getConnection().setAutoCommit(true)
            }
        }
    }


    suspend fun insertListAsync(entities: List<T>) {
        db.prepareAndUseStatementAsync(makeSql(false)) { stmt ->
            try {
                stmt.getConnection().setAutoCommit(false)
                entities.forEach {
                    bindPreparedStmtToEntity(stmt, it)
                    stmt.executeUpdateAsyncKmp()
                }
                stmt.getConnection().commit()
            }finally {
                stmt.getConnection().setAutoCommit(true)
            }
        }
    }


}
