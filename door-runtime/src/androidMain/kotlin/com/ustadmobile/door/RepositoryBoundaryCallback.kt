package com.ustadmobile.door

import androidx.paging.PagedList
import io.github.aakira.napier.Napier
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * This is a PagedList.boundaryCallback that automatically calls a given LoadHelper to load more data.
 */
class RepositoryBoundaryCallback<T : Any>(val loadHelper: RepositoryLoadHelper<List<T>>): PagedList.BoundaryCallback<T>() {

    val loadCount = AtomicInteger()

    fun loadMore() {
        //We need to set runAgain to true when the user gets to the bottom of the list and it's time
        // to load more entries from the server (e.g. repeat the request).
        val runAgain = loadCount.getAndIncrement() != 0
        GlobalScope.launch(Dispatchers.IO) {
            try {
                loadHelper.doRequest(resetAttemptCount = true, runAgain = runAgain)
            }catch(e: Exception) {
                Napier.e("Exception running loadHelper", e)
            }
        }
    }

    override fun onZeroItemsLoaded() {
        loadMore()
    }

    override fun onItemAtEndLoaded(itemAtEnd: T) {
        loadMore()
    }
}