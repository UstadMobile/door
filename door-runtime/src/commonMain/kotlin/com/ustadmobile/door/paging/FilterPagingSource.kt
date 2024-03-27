package com.ustadmobile.door.paging

import app.cash.paging.PagingSource
import app.cash.paging.PagingSourceLoadParams
import app.cash.paging.PagingSourceLoadResult
import app.cash.paging.PagingState
import kotlinx.atomicfu.atomic

/**
 * Similar concept to FilterInputStream / FilterOutputStream - will generally just pass things through. Child classes
 * can change
 */
abstract class FilterPagingSource<Key: Any, Value: Any>(
    private val src: PagingSource<Key, Value>,
): PagingSource<Key, Value>() {

    private val srcInvalidateCallbackRegistered = atomic(false)

    private val invalidated = atomic(false)

    override val jumpingSupported: Boolean
        get() = src.jumpingSupported
    override val keyReuseSupported: Boolean
        get() = src.keyReuseSupported

    private val srcInvalidatedCallback: () -> Unit = {
        onSrcInvalidated()
    }

    private fun onSrcInvalidated() {
        src.unregisterInvalidatedCallback(srcInvalidatedCallback)
        if(!invalidated.getAndSet(true)) {
            invalidate()
        }
    }

    override fun getRefreshKey(state: PagingState<Key, Value>): Key? {
        return src.getRefreshKey(state)
    }

    override suspend fun load(params: PagingSourceLoadParams<Key>): PagingSourceLoadResult<Key, Value> {
        if(!srcInvalidateCallbackRegistered.getAndSet(true)) {
            src.registerInvalidatedCallback(srcInvalidatedCallback)
        }

        return src.load(params)
    }
}