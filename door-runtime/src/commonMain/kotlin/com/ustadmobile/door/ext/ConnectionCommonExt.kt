package com.ustadmobile.door.ext

import com.ustadmobile.door.jdbc.*

/**
 * If the receiver Connection is an AsyncConnection, will use the Async setAutoCommit. Otherwise fallback to the
 * synchronous implementation
 */
suspend fun Connection.setAutoCommitAsyncOrFallback(commit: Boolean) {
    if(this is AsyncConnection) {
        setAutoCommitAsync(commit)
    }else{
        setAutoCommit(commit)
    }
}

suspend fun Connection.commitAsyncOrFallback() {
    if(this is AsyncConnection) {
        commitAsync()
    }else {
        commit()
    }
}

/**
 * If the receiver Connection is an AsyncConnection, will use the Async setAutoCommit. Otherwise fallback to the
 * synchronous implementation
 */
suspend fun Connection.closeAsyncOrFallback() {
    if(this is AsyncCloseable) {
        closeAsync()
    }else {
        close()
    }
}

suspend fun Connection.prepareStatementAsyncOrFallback(
    sql: String,
    autoGeneratedKeys: Int = StatementConstantsKmp.NO_GENERATED_KEYS
): PreparedStatement {
    return if(this is AsyncConnection) {
        prepareStatementAsync(sql, autoGeneratedKeys)
    }else {
        prepareStatement(sql, autoGeneratedKeys)
    }
}
