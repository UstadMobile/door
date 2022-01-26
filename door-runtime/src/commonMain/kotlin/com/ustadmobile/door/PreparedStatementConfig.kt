package com.ustadmobile.door

import com.ustadmobile.door.jdbc.StatementConstantsKmp

/**
 * Wrapper that contains the information needed to prepare a statement. Used by generated code.
 */
data class PreparedStatementConfig(
    val sql: String,
    val hasListParams: Boolean = false,
    val generatedKeys: Int = StatementConstantsKmp.NO_GENERATED_KEYS,
    val timeoutSeconds: Int = STATEMENT_DEFAULT_TIMEOUT_SECS,
    /**
     * Where needed, a different query can be specified to run on Postgres. It should still have the same number of
     * parameters in the same order.
     */
    val postgreSql: String? = null
) {
    fun sqlToUse(dbType: Int) = if(dbType == DoorDbType.SQLITE) {
        sql
    }else {
        postgreSql ?: sql
    }

    companion object {

        const val STATEMENT_DEFAULT_TIMEOUT_SECS = 10

    }
}
