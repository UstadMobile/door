//The checks/casts are not useless: The IDE fails to understand expect-actual class hierarchy
@file:Suppress("USELESS_IS_CHECK", "CAST_NEVER_SUCCEEDS")

package com.ustadmobile.door.paging

import app.cash.paging.*

/**
 * This is based on Room RoomPagingUtil.kt to try and ensure that behavior is consistent with Room.
 */

const val INITIAL_ITEM_COUNT = -1

val INVALID = PagingSourceLoadResultInvalid<Any, Any>()

/**
 * Calculates query limit based on LoadType.
 *
 * Prepend: If requested loadSize is larger than available number of items to prepend, it will
 * query with OFFSET = 0, LIMIT = prevKey
 */
fun getLimit(params: PagingSourceLoadParams<Int>, key: Int): Int {
    return when(params) {
        is PagingSourceLoadParamsPrepend<*> -> {
            if(key< params.loadSize) {
                key
            }else {
                params.loadSize
            }
        }
        else -> params.loadSize
    }
}

fun getOffset(params: PagingSourceLoadParams<Int>, key: Int, itemCount: Int): Int {
    return when(params) {
        is PagingSourceLoadParamsPrepend<*> -> {
            if(key < params.loadSize) {
                0
            }else {
                key - params.loadSize
            }
        }
        is PagingSourceLoadParamsAppend<*> -> key
        is PagingSourceLoadParamsRefresh<*> -> {
            if (key >= itemCount) {
                maxOf(0, itemCount - params.loadSize)
            } else {
                key
            }
        }else -> throw IllegalStateException(
            "Not really possible - Just here because compiler does not fully understand expect/actual"
        )
    }
}


suspend fun <Value: Any> queryDatabase(
    params: PagingSourceLoadParams<Int>,
    loadRows: suspend (limit: Int, offset: Int) -> List<Value>,
    itemCount: Int,
) : PagingSourceLoadResult<Int, Value> {
    val key = params.key ?: 0
    val limit: Int = getLimit(params, key)
    val offset = getOffset(params, key, itemCount)
    val data = loadRows(limit, offset)
    val nextPosToLoad = offset + data.size
    val nextKey =
        if (data.isEmpty() || data.size < limit || nextPosToLoad >= itemCount) {
            null
        } else {
            nextPosToLoad
        }
    val prevKey = if (offset <= 0 || data.isEmpty()) null else offset
    return PagingSourceLoadResultPage<Int, Value>(
        data = data,
        prevKey = prevKey,
        nextKey = nextKey,
        itemsBefore = offset,
        itemsAfter = maxOf(0, itemCount - nextPosToLoad)
    ) as PagingSourceLoadResult<Int, Value>
}

fun <Value : Any> PagingState<Int, Value>.getClippedRefreshKey(): Int? {
    return when (val anchorPosition = anchorPosition) {
        null -> null
        /**
         *  It is unknown whether anchorPosition represents the item at the top of the screen or item at
         *  the bottom of the screen. To ensure the number of items loaded is enough to fill up the
         *  screen, half of loadSize is loaded before the anchorPosition and the other half is
         *  loaded after the anchorPosition -- anchorPosition becomes the middle item.
         */
        else -> maxOf(0, anchorPosition - (config.initialLoadSize / 2))
    }
}


