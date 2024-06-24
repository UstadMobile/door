package com.ustadmobile.door.paging

import app.cash.paging.PagingSource
import app.cash.paging.PagingSourceLoadParams
import app.cash.paging.PagingSourceLoadResult
import app.cash.paging.PagingState

/**
 * Similar concept to FilterInputStream / FilterOutputStream - will generally just pass things through. Child classes
 * can change
 */
abstract class FilterPagingSource<Key: Any, Value: Any>(
    private val src: PagingSource<Key, Value>,
): DelegatedInvalidationPagingSource<Key, Value>(invalidationDelegate = src) {

    override val jumpingSupported: Boolean
        get() = src.jumpingSupported

    override val keyReuseSupported: Boolean
        get() = src.keyReuseSupported


    override fun getRefreshKey(state: PagingState<Key, Value>): Key? {
        return src.getRefreshKey(state)
    }

    override suspend fun load(params: PagingSourceLoadParams<Key>): PagingSourceLoadResult<Key, Value> {
        registerInvalidationCallbackIfNeeded()
        return src.load(params)
    }
}