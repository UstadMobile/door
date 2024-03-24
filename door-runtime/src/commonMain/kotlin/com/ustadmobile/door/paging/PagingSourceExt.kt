package com.ustadmobile.door.paging

import app.cash.paging.PagingSource
import app.cash.paging.PagingSourceLoadParams
import app.cash.paging.PagingSourceLoadResultError
import app.cash.paging.PagingSourceLoadResultPage

@Suppress("CAST_NEVER_SUCCEEDS")
suspend fun <Key: Any, Value: Any> PagingSource<Key, Value>.loadPageDataOrEmptyList(
    loadParams: PagingSourceLoadParams<Key>
): List<Value> {
    return (load(loadParams) as? PagingSourceLoadResultPage<Key, Value>)?.data ?: emptyList()
}


/**
 * Where PULL_REPLICATE_ENTITIES is used together with a PagingSource, the RemoteMediator needs to know if the end of
 * pagination has been reached. This is provided as a http header
 */
@Suppress("CAST_NEVER_SUCCEEDS", "USELESS_IS_CHECK", "UNCHECKED_CAST")
suspend fun  <Key: Any, Value: Any> PagingSource<Key, Value>.loadPageDataForHttp(
    loadParams: PagingSourceLoadParams<Key>
): PagingSourceReplicatePullHttpResponseResult<Value> {
    val pagingResult = load(loadParams)
    if(pagingResult is PagingSourceLoadResultPage<*, *>) {
        return PagingSourceReplicatePullHttpResponseResult(
            data = pagingResult.data as List<Value>,
            endOfPaginationReached = pagingResult.nextKey == null
        )
    }else {
        val error = (pagingResult as? PagingSourceLoadResultError<*, *>)?.throwable
        if(error != null) {
            throw IllegalStateException(error.message, error.cause)
        }

        return PagingSourceReplicatePullHttpResponseResult(
            data = emptyList(),
            endOfPaginationReached = true
        )
    }
}

