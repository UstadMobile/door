package com.ustadmobile.door.paging

import app.cash.paging.*
import com.ustadmobile.door.ext.withDoorTransactionAsync
import com.ustadmobile.door.room.InvalidationTrackerObserver
import com.ustadmobile.door.room.RoomDatabase
import kotlinx.atomicfu.atomic
import kotlin.math.max


/**
 * Implementation of an offset-limit PagingSource for JVM and JS.
 */
@Suppress("CAST_NEVER_SUCCEEDS")
abstract class DoorLimitOffsetPagingSource<Value: Any>(
    private val db: RoomDatabase,
    private val tableNames: Array<String>,
): PagingSource<Int, Value>() {

    private inner class InvalidationTracker(): InvalidationTrackerObserver(tableNames) {

        private val registered = atomic(false)

        fun registerIfNeeded() {
            if(!registered.getAndSet(true))
                db.invalidationTracker.addWeakObserver(this)
        }

        override fun onInvalidated(tables: Set<String>) {
            invalidate()
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
    ): PagingSourceLoadResult<Int, Value> {
        invalidationTracker.registerIfNeeded()

        val offset = params.key ?: 0

        val (items, count) = db.withDoorTransactionAsync {
            loadRows(params.loadSize, offset) to countRows()
        }
        return PagingSourceLoadResultPage(
            data = items,
            prevKey = if(offset > 0) max(0, offset - params.loadSize) else null,
            nextKey = if(offset + params.loadSize < count) offset + params.loadSize else null,
        ) as PagingSourceLoadResult<Int, Value>
    }

    override fun getRefreshKey(state: PagingState<Int, Value>): Int {
        return state.anchorPosition ?: 0
    }

}
