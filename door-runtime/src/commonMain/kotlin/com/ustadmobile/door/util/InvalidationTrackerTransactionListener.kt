package com.ustadmobile.door.util

import com.ustadmobile.door.jdbc.Connection

/**
 * Interface that may be implemented by an invalidation tracker if needed. It allows the invalidation tracker to setup triggers before a
 * transaction takes place, check the results after the transaction is done, and listen for when the transaction is committed.
 */
internal interface InvalidationTrackerTransactionListener {

    /**
     * The transaction is open, but no other code has used the transaction yet. This can be used to setup triggers etc.
     */
    fun beforeTransactionBlock(connection: Connection)

    /**
     * The transaction is open, but no other code has used the transaction yet. This can be used to setup triggers etc.
     */
    suspend fun beforeAsyncTransasctionBlock(connection: Connection)

    /**
     * The transaction is open and all code that could make any modification has been run. This can be used to check
     * for changes etc.
     */
    fun afterTransactionBlock(connection: Connection)

    /**
     * The transaction is open and all code that could make any modification has been run. This can be used to check
     * for changes etc.
     */
    suspend fun afterAsyncTransactionBlock(connection: Connection)

    /**
     * The transaction is now committed
     */
    fun afterTransactionCommitted(connection: Connection)

    /**
     * The transaction is now committed
     */
    suspend fun afterTransactionCommittedAsync(connection: Connection)


}