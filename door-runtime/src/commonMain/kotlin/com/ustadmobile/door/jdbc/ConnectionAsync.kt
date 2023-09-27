package com.ustadmobile.door.jdbc

/**
 * Implemented by connections on Kotlin/JS where these cannot be done synchronously. these functions are used when
 * available by RoomDatabaseJdbcImplHelperCommon useConnectionAsync
 */
interface ConnectionAsync {

    suspend fun setAutoCommitAsync(autoCommit: Boolean)

    suspend fun commitAsync()

    suspend fun rollbackAsync()

}