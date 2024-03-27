package com.ustadmobile.door.paging

import app.cash.paging.PagingSource
import app.cash.paging.PagingSourceLoadParams
import app.cash.paging.PagingSourceLoadResult

/**
 * Used to receive onLoad callback (e.g. by DoorOffsetLimitRemoteMediator) to trigger http
 */
@Suppress("unused")
class PagingSourceInterceptor<Key: Any, Value: Any>(
    src: PagingSource<Key, Value>,
    private val onLoad: (params: PagingSourceLoadParams<Key>) -> Unit
) : FilterPagingSource<Key, Value>(src){

    override suspend fun load(params: PagingSourceLoadParams<Key>): PagingSourceLoadResult<Key, Value> {
        onLoad(params)
        return super.load(params)
    }

}