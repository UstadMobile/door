package com.ustadmobile.door.paging

import app.cash.paging.PagingSourceLoadResult
import app.cash.paging.PagingSourceLoadResultPage

fun <Value: Any> PagingSourceLoadResult<*, Value>.pageDataOrEmpty(): List<Value> {
    return (this as? PagingSourceLoadResultPage<*, Value>)?.data ?: emptyList()
}
