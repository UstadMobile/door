package com.ustadmobile.door.paging

import app.cash.paging.*
import com.ustadmobile.door.ext.withDoorTransactionAsync
import com.ustadmobile.door.room.InvalidationTrackerObserver
import com.ustadmobile.door.room.RoomDatabase
import com.ustadmobile.door.util.TransactionMode
import io.github.aakira.napier.Napier
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Implementation of an offset-limit PagingSource for JVM and JS. Based on
 * Room's LimitOffsetPagingSource.kt
 */
//Cast never succeed errors are caused by failure to IDE failure to understand expect-actual
@Suppress("CAST_NEVER_SUCCEEDS")
abstract class DoorLimitOffsetPagingSource<Value: Any>(
    private val db: RoomDatabase,
    private val tableNames: Array<String>,
): PagingSource<Int, Value>() {

    private val itemCount = atomic(INITIAL_ITEM_COUNT)

    private val invalidated = atomic(false)

    private inner class InvalidationTracker: InvalidationTrackerObserver(tableNames) {

        private val registered = atomic(false)

        fun registerIfNeeded() {
            if(!registered.getAndSet(true))
                db.invalidationTracker.addWeakObserver(this)
        }

        override fun onInvalidated(tables: Set<String>) {
            if(!invalidated.getAndSet(true)) {
                Napier.d("DoorLimitOffsetPagingSource: invalidated tables=${tables.joinToString()}")
                invalidate()
            }
        }
    }

    private val invalidationTracker = InvalidationTracker()

    abstract suspend fun loadRows(_limit: Int, _offset: Int): List<Value>

    abstract suspend fun countRows(): Int

    /**
     * Note: the expect class for paging-multiplatform PagingSourceLoadResult does not declare LoadResultPage etc as a
     * child class of PagingSourceLoadResult. This requires the result to be casted in order for commonMain compilation
     * to succeed.
     */
    override suspend fun load(
        params: PagingSourceLoadParams<Int>
    ): PagingSourceLoadResult<Int, Value> = withContext(Dispatchers.Default) {
        invalidationTracker.registerIfNeeded()
        val tmpCount = itemCount.value
        if(tmpCount == INITIAL_ITEM_COUNT) {
            initialLoad(params)
        }else {
            nonInitialLoad(params, tmpCount)
        }
    }

    private suspend fun initialLoad(
        params: PagingSourceLoadParams<Int>
    ): PagingSourceLoadResult<Int, Value> = db.withDoorTransactionAsync(
        TransactionMode.READ_ONLY
    ) {
        val tempCount = countRows()
        itemCount.value = tempCount
        queryDatabase(
            params = params,
            loadRows = { limit, offset ->
                loadRows(limit, offset)
            },
            itemCount = tempCount
        )
    }

    private suspend fun nonInitialLoad(
        params: PagingSourceLoadParams<Int>,
        tempCount: Int
    ): PagingSourceLoadResult<Int, Value> {
        return if(invalidated.value) {
            INVALID as PagingSourceLoadResult<Int, Value>
        }else {
            queryDatabase(
                params = params,
                loadRows = { limit, offset ->
                    loadRows(limit, offset)
                },
                itemCount = tempCount,
            )
        }
    }

    override fun getRefreshKey(state: PagingState<Int, Value>): Int? {
        return state.getClippedRefreshKey()
    }

}
