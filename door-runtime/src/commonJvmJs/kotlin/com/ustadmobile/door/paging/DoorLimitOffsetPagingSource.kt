package com.ustadmobile.door.paging

import app.cash.paging.*
import com.ustadmobile.door.ext.withDoorTransactionAsync
import com.ustadmobile.door.room.RoomDatabase
import kotlin.math.max

/**
 * Implementation of an offset-limit PagingSource for JVM and JS.
 */
abstract class DoorLimitOffsetPagingSource<Value: Any>(
    private val db: RoomDatabase,
): PagingSource<Int, Value>() {

    abstract suspend fun loadRows(_limit: Int, _offset: Int): List<Value>

    abstract suspend fun countRows(): Int

    override suspend fun load(
        params: PagingSourceLoadParams<Int>
    ): PagingSourceLoadResult<Int, Value> {
        val offset = params.key ?: 0

        val (items, count) = db.withDoorTransactionAsync {
            loadRows(params.loadSize, offset) to countRows()
        }
        return PagingSourceLoadResultPage(
            data = items,
            prevKey = if(offset > 0) max(0, offset - params.loadSize) else null,
            nextKey = if(offset + params.loadSize < count) offset + params.loadSize else null
        ) as PagingSourceLoadResult<Int, Value>
    }

    override fun getRefreshKey(state: PagingState<Int, Value>): Int {
        return state.anchorPosition ?: 0
    }

}
