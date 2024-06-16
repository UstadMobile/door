package com.ustadmobile.door.paging

import app.cash.paging.PagingSourceLoadParams
import com.ustadmobile.door.ext.concurrentSafeListOf
import com.ustadmobile.door.util.systemTimeInMillis
import io.github.aakira.napier.Napier
import kotlinx.coroutines.*

/**
 * A "normal" RemoteMediator doesn't work in the following situations:
 *   1) Let's say a user opens a list that contains 1,000 items. When:
 *      a) The user loads the initial list e.g. the first 100 items
 *      b) The user filters the query e.g. that loads items 500-550
 *      c) The user clears the filter
 *     What will happen: The RemoteMediator won't attempt to load anything until the user reaches
 *     the end of the (local) list. The user will see items 1-100, then items 500-550. Items 101-499
 *     will be missing.
 *
 *   2) The user has opened the list before
 *     a) The user loads the initial list - the RemoteMediator runs a refresh, items 1-100 will be
 *        updated if they changed on the server
 *     b) The user scrolls down to view items 101-200.
 *     Viewing items 101-200 will not trigger the RemoteMediator to refresh.
 *
 * DoorOffsetLimitRemoteMediator works by receiving an event each time the underlying (local)
 * PagingSource.load function is called. Each time PagingSource.load is called it will:
 *   1) Calculate the range that the PagingSource would be loading (e.g. the offset and limit)
 *      based on the PagingSourceLoadParams using the same logic as is used by the OffsetLimit
 *      PagingSource itself.
 *   2) Calculate an expanded range that should be fetched from the server based on
 *      prefetchDistance e.g. items from (loadparams offset minus prefetchDistance) until (
 *      loadparams offset plus loadparams limit plus prefetchDistance).
 *   3) Reduce the range to load from the server based on ranges that were already loaded within
 *      the time to live which have not been invalidated.
 *   4) If anything remains to be loaded, then invoke onRemoteLoad for the required range.
 *
 *  Finally, the RemoteMediator will normally run three requests : an initial refresh, a prepend,
 *  and then an append. Those can be handled using just one request.
 *
 *  Efficiency is further improved using the threshold e.g. if the prefetchDistance is 100,
 *  items 1-100 have already been fetched from the remote, and the pagingsource just loaded items
 *  10-20, then normally a request for items 101-120 would be sent. If a threshold is set (e.g. to
 *  wait until the prefetch exceeds the known range by 50), this could further reduce the number of
 *  requests made.
 *
 *  DoorOffsetLimitRemoteMediator is used by Compose and React functions (e.g. rememberDoorRepositoryPager
 *  and useDoorRemoteMediator in the main UstadMobile project).
 *
 * @param prefetchDistance the distance by which to prefetch before and after each page load
 * @param prefetchThreshold when all the data required by a PagingSource load itself has already
 *        been loaded, however more data would need to be loaded to reach the prefetchDistance, the
 *        threshold is the minimum number of prefetch items to initiate a request (as outlined above).
 * @param onRemoteLoad A remote load function to invoke when ranges need to be fetched.
 */
class DoorOffsetLimitRemoteMediator(
    private val prefetchDistance: Int = 100,
    private val prefetchThreshold: Int = (prefetchDistance / 2),
    private val onRemoteLoad: OnRemoteLoad,
) {

    private val scope = CoroutineScope(Dispatchers.Default + Job())

    fun interface OnRemoteLoad {
        suspend operator fun invoke(offset: Int, limit: Int)

    }

    data class LoadedRange(
        val offset: Int,
        val limit: Int,
        val time: Long,
    )

    private val loadedRanges = concurrentSafeListOf<LoadedRange>()

    /**
     * Must be invoked when the underlying PagingSource is invoked
     */
    fun onLoad(params: PagingSourceLoadParams<Int>) {
        val pagingOffset = getOffset(params, (params.key ?: 0), Int.MAX_VALUE)
        val pagingLimit: Int = getLimit(params, (params.key ?: 0))

        /*
         * Set a range for the items that we want to have in the local database, expanding the
         * paging source load params by prefetchDistance
         */
        val rangeOffset = maxOf(0,pagingOffset - prefetchDistance)

        //The range offset may (or may not if it is already zero) be lower than the paging
        val rangeLimit = pagingLimit + (pagingOffset - rangeOffset) + prefetchDistance

        //Improvement to be made - trim the load range based on items already recently loaded.
        var loadOffset = rangeOffset

        //Go forward through all the ranges we already have loaded, push back the loadOffset if we already
        // have that range loaded
        loadedRanges.sortedBy { it.offset }.forEach {
            if(it.offset <= rangeOffset && (it.offset + it.limit) > loadOffset)
                loadOffset = (it.offset + it.limit)
        }

        if(loadOffset >= (rangeOffset + rangeLimit)) {
            Napier.d { "DoorOffsetLimitRemoteMediator: already loaded everything required." }
            return
        }

        var loadEnd = (rangeOffset + rangeLimit)

        loadedRanges.sortedByDescending { it.offset }.forEach {
            if(it.offset < loadEnd && (it.offset + it.limit) > loadEnd)
                loadEnd = it.offset
        }

        val loadLimit = loadEnd - loadOffset

        //Check if the range to load overlaps the range actually being loaded by the pagingSource
        val loadPagingOverlap = minOf(pagingOffset + pagingLimit, loadOffset + loadLimit) -
                maxOf(pagingOffset, loadOffset)

        //Calculate (if required) how many items would be prefetched
        val prefetchSize = if(loadPagingOverlap > 0) {
            -1 //Doesn't matter, loading must happen anyway
        }else {
            var alreadyLoadedStart = pagingOffset
            loadedRanges.sortedByDescending { it.offset }.forEach {
                if(it.offset < alreadyLoadedStart && it.offset + it.limit >= alreadyLoadedStart)
                    alreadyLoadedStart = it.offset
            }

            var alreadyLoadedEnd = (pagingOffset + pagingLimit)
            loadedRanges.sortedBy { it.offset }.forEach {
                if(it.offset <= alreadyLoadedEnd && (it.offset + it.limit) > alreadyLoadedEnd) {
                    alreadyLoadedEnd = it.offset + it.limit
                }
            }

            (rangeOffset - alreadyLoadedStart) + ((rangeOffset + rangeLimit) - alreadyLoadedEnd)
        }


        //Run the request if a) there is any overlap with the range requested by the PagingSource or
        // b) the number of items to prefetch exceeds the prefetch threshold
        if(loadPagingOverlap > 0 || prefetchSize > prefetchThreshold) {
            scope.launch {
                try {
                    onRemoteLoad(loadOffset, loadLimit)
                    loadedRanges.add(LoadedRange(loadOffset, loadLimit, systemTimeInMillis()))
                }catch(e: Throwable) {
                    Napier.w("Attempted to load from offset=$loadOffset limit=$loadLimit faled ", e)
                }
            }
        }
    }

    fun invalidate() {
        loadedRanges.clear()
    }

    fun cancel() {
        scope.cancel()
    }

}