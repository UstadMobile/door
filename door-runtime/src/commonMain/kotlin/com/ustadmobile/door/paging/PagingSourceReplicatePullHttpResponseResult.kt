package com.ustadmobile.door.paging


data class PagingSourceReplicatePullHttpResponseResult<Value: Any>(
    val data: List<Value>,
    val endOfPaginationReached: Boolean,
)
