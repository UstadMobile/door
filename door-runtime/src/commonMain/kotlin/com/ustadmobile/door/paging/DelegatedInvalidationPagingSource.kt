package com.ustadmobile.door.paging

import app.cash.paging.PagingSource
import kotlinx.atomicfu.atomic

/**
 * Contains the logic required for a child paging source to invalidate itself when an underlying paging source is
 * invalidated.
 *
 * The child PagingSource MUST call registerInvalidationCallbackIfNeeded in its onLoad function
 */
abstract class DelegatedInvalidationPagingSource<Key: Any, Value: Any>(
    private val invalidationDelegate: PagingSource<*, *>
) : PagingSource<Key, Value>(){

    private val srcInvalidateCallbackRegistered = atomic(false)

    private val invalidated = atomic(false)

    private val srcInvalidatedCallback: () -> Unit = {
        onSrcInvalidated()
    }

    private fun onSrcInvalidated() {
        invalidationDelegate.unregisterInvalidatedCallback(srcInvalidatedCallback)
        if(!invalidated.getAndSet(true)) {
            invalidate()
        }
    }

    protected fun registerInvalidationCallbackIfNeeded() {
        if(!srcInvalidateCallbackRegistered.getAndSet(true)) {
            invalidationDelegate.registerInvalidatedCallback(srcInvalidatedCallback)
        }
    }

}