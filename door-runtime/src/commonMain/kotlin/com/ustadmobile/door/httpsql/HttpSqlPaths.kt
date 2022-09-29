package com.ustadmobile.door.httpsql

object HttpSqlPaths {

    const val PATH_CONNECTION_OPEN = "openConnection"

    const val PATH_CONNECTION_CLOSE = "closeConnection"

    const val PATH_STATEMENT_QUERY = "statementQuery"

    const val PATH_STATEMENT_UPDATE = "statementUpdate"

    const val PATH_PREPARE_STATEMENT = "prepareStatement"

    const val PATH_PREPARED_STATEMENT_QUERY = "preparedStatementQuery"

    const val PATH_PREPARED_STATEMENT_UPDATE = "preparedStatementUpdate"

    const val PATH_PREPARED_STATEMENT_CLOSE = "preparedStatementClose"

    const val PARAM_CONNECTION_ID = "connectionId"

    const val PARAM_PREPAREDSTATEMENT_ID = "preparedStatementId"

    /**
     * JSON Object key query results - actual rows as per query
     */
    const val KEY_ROWS = "rows"

    const val KEY_UPDATES = "updates"



}