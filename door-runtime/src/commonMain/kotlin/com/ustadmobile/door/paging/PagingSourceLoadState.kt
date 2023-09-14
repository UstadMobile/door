package com.ustadmobile.door.paging

data class PagingSourceLoadState<Key: Any>(
    val activeRequests: List<PagingRequest<Key>> = emptyList(),
    val failedRequests: List<PagingRequest<Key>> = emptyList(),
    val completedRequests: List<PagingRequest<Key>> = emptyList(),
) {

    data class PagingRequest<Key: Any>(
        val key: Key?
    )

    fun copyWithNewRequest(request: PagingRequest<Key>) : PagingSourceLoadState<Key> {
        return copy(
            activeRequests = activeRequests + listOf(request),
        )
    }

    fun copyWhenRequestFailed(request: PagingRequest<Key>) : PagingSourceLoadState<Key> {
        return copy(
            activeRequests = activeRequests.filter { it != request },
            failedRequests = failedRequests + listOf(request),
        )
    }

    fun copyWhenRequestCompleted(request: PagingRequest<Key>) : PagingSourceLoadState<Key> {
        return copy(
            activeRequests = activeRequests.filter { it != request },
            completedRequests = activeRequests + listOf(request),
        )
    }

}