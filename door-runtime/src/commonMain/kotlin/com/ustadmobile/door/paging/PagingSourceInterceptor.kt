package com.ustadmobile.door.paging

import app.cash.paging.PagingSource
import app.cash.paging.PagingSourceLoadParams
import app.cash.paging.PagingSourceLoadResult

/**
 * This is used to trigger DoorOffsetLimitRemoteMediator by effects and hooks used in Compose and React. It allows
 * transparent interception of onLoad events by the underlying paging mechanism (eg. Compose Pager or Tanstack) and
 * triggers a callback (e.g. to the DoorOffsetLimitRemoteMediator).
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