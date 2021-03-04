package com.ustadmobile.door.annotation

/**
 * This annotation is applied to a Query parameter for a query that returns syncable results. E.g.
 * Where there is a query:
 *
 * \@Query("SELECT * FROM SomeSyncableEntity LIMIT :maxResults")
 *
 * The query will be refactored on the server side to remove entities that have already been sent
 * to the given client. E.g.
 *
 * "SELECT * FROM (SELECT * FROM SomeSyncableEntity) WHERE (entityNotReceivedByClientYet) LIMIT :maxResults"
 *
 * If the LIMIT is not removed from the original query, this could lead to a situation where no
 * results would be returned because the first results (e.g. up to maxResults) have already been
 * sent to the client.
 *
 * Use this as follows:
 *
 * \@Query("SELECT * FROM SomeSyncableEntity LIMIT :maxResults")
 * fun findAll(\@SyncableLimitParam maxResults: Int)
 */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class SyncableLimitParam()
