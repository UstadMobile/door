package com.ustadmobile.door.paging

import app.cash.paging.PagingSource
import app.cash.paging.PagingSourceLoadParams
import app.cash.paging.PagingSourceLoadResultPage

@Suppress("CAST_NEVER_SUCCEEDS")
suspend fun <Key: Any, Value: Any> PagingSource<Key, Value>.loadPageDataOrEmptyList(
    loadParams: PagingSourceLoadParams<Key>
): List<Value> {
    return (load(loadParams) as? PagingSourceLoadResultPage<Key, Value>)?.data ?: emptyList()
}
