package com.ustadmobile.door.paging

import androidx.annotation.IntRange

actual abstract class PagingSource<Key: Any, Value: Any> actual constructor(){

    /**
     * Loading API for [PagingSource].
     *
     * Implement this method to trigger your async load (e.g. from database or network).
     */
    actual abstract suspend fun load(params: LoadParams<Key>): LoadResult<Key, Value>

    /**
     * Provide a [Key] used for the initial [load] for the next [PagingSource] due to invalidation
     * of this [PagingSource]. The [Key] is provided to [load] via [LoadParams.key].
     *
     * The [Key] returned by this method should cause [load] to load enough items to
     * fill the viewport *around* the last accessed position, allowing the next generation to
     * transparently animate in. The last accessed position can be retrieved via
     * [state.anchorPosition][PagingState.anchorPosition], which is typically
     * the *top-most* or *bottom-most* item in the viewport due to access being triggered by binding
     * items as they scroll into view.
     *
     * For example, if items are loaded based on integer position keys, you can return
     * `( (state.anchorPosition ?: 0) - state.config.initialLoadSize / 2).coerceAtLeast(0)`.
     *
     * Alternately, if items contain a key used to load, get the key from the item in the page at
     * index [state.anchorPosition][PagingState.anchorPosition] then try to center it based on
     * `state.config.initialLoadSize`.
     *
     * @param state [PagingState] of the currently fetched data, which includes the most recently
     * accessed position in the list via [PagingState.anchorPosition].
     *
     * @return [Key] passed to [load] after invalidation used for initial load of the next
     * generation. The [Key] returned by [getRefreshKey] should load pages centered around
     * user's current viewport. If the correct [Key] cannot be determined, `null` can be returned
     * to allow [load] decide what default key to use.
     */
    actual abstract fun getRefreshKey(state: PagingState<Key, Value>): Key?
}

actual sealed class LoadResult<Key : Any, Value : Any>  {

    /**
     * Error result object for [PagingSource.load].
     *
     * This return type indicates an expected, recoverable error (such as a network load
     * failure). This failure will be forwarded to the UI as a [LoadState.Error], and may be
     * retried.
     *
     * @sample androidx.paging.samples.pageKeyedPagingSourceSample
     */
    public data class Error<Key : Any, Value : Any>(
        val throwable: Throwable
    ) : LoadResult<Key, Value>()

    /**
     * Invalid result object for [PagingSource.load]
     *
     * This return type can be used to terminate future load requests on this [PagingSource]
     * when the [PagingSource] is not longer valid due to changes in the underlying dataset.
     *
     * For example, if the underlying database gets written into but the [PagingSource] does
     * not invalidate in time, it may return inconsistent results if its implementation depends
     * on the immutability of the backing dataset it loads from (e.g., LIMIT OFFSET style db
     * implementations). In this scenario, it is recommended to check for invalidation after
     * loading and to return LoadResult.Invalid, which causes Paging to discard any
     * pending or future load requests to this PagingSource and invalidate it.
     *
     * Returning [Invalid] will trigger Paging to [invalidate] this [PagingSource] and
     * terminate any future attempts to [load] from this [PagingSource]
     */
    public class Invalid<Key : Any, Value : Any> : LoadResult<Key, Value>()

    /**
     * Success result object for [PagingSource.load].
     *
     * @sample androidx.paging.samples.pageKeyedPage
     * @sample androidx.paging.samples.pageIndexedPage
     */
    public data class Page<Key : Any, Value : Any> constructor(
        /**
         * Loaded data
         */
        val data: List<Value>,
        /**
         * [Key] for previous page if more data can be loaded in that direction, `null`
         * otherwise.
         */
        val prevKey: Key?,
        /**
         * [Key] for next page if more data can be loaded in that direction, `null` otherwise.
         */
        val nextKey: Key?,
        /**
         * Optional count of items before the loaded data.
         */
        @IntRange(from = COUNT_UNDEFINED.toLong())
        val itemsBefore: Int = COUNT_UNDEFINED,
        /**
         * Optional count of items after the loaded data.
         */
        @IntRange(from = COUNT_UNDEFINED.toLong())
        val itemsAfter: Int = COUNT_UNDEFINED
    ) : LoadResult<Key, Value>() {

        /**
         * Success result object for [PagingSource.load].
         *
         * @param data Loaded data
         * @param prevKey [Key] for previous page if more data can be loaded in that direction,
         * `null` otherwise.
         * @param nextKey [Key] for next page if more data can be loaded in that direction,
         * `null` otherwise.
         */
        public constructor(
            data: List<Value>,
            prevKey: Key?,
            nextKey: Key?
        ) : this(data, prevKey, nextKey, COUNT_UNDEFINED, COUNT_UNDEFINED)

        init {
            require(itemsBefore == COUNT_UNDEFINED || itemsBefore >= 0) {
                "itemsBefore cannot be negative"
            }

            require(itemsAfter == COUNT_UNDEFINED || itemsAfter >= 0) {
                "itemsAfter cannot be negative"
            }
        }

        public companion object {
            public const val COUNT_UNDEFINED: Int = Int.MIN_VALUE

            @Suppress("MemberVisibilityCanBePrivate") // Prevent synthetic accessor generation.
            internal val EMPTY = Page(emptyList(), null, null, 0, 0)

            @Suppress("UNCHECKED_CAST") // Can safely ignore, since the list is empty.
            internal fun <Key : Any, Value : Any> empty() = EMPTY as Page<Key, Value>
        }
    }

}


actual sealed class LoadParams<Key : Any> actual constructor(
    /**
     * Requested number of items to load.
     *
     * Note: It is valid for [PagingSource.load] to return a [LoadResult] that has a different
     * number of items than the requested load size.
     */
    val loadSize: Int,
    /**
     * From [PagingConfig.enablePlaceholders], true if placeholders are enabled and the load
     * request for this [LoadParams] should populate [LoadResult.Page.itemsBefore] and
     * [LoadResult.Page.itemsAfter] if possible.
     */
    val placeholdersEnabled: Boolean,
) {

    actual abstract val key: Key?

    /**
     * Params for an initial load request on a [PagingSource] from [PagingSource.load] or a
     * refresh triggered by [invalidate].
     */
    public class Refresh<Key : Any> constructor(
        override val key: Key?,
        loadSize: Int,
        placeholdersEnabled: Boolean,
    ) : LoadParams<Key>(
        loadSize = loadSize,
        placeholdersEnabled = placeholdersEnabled,
    )

    /**
     * Params to load a page of data from a [PagingSource] via [PagingSource.load] to be
     * appended to the end of the list.
     */
    public class Append<Key : Any> constructor(
        override val key: Key,
        loadSize: Int,
        placeholdersEnabled: Boolean,
    ) : LoadParams<Key>(
        loadSize = loadSize,
        placeholdersEnabled = placeholdersEnabled,
    )

    /**
     * Params to load a page of data from a [PagingSource] via [PagingSource.load] to be
     * prepended to the start of the list.
     */
    public class Prepend<Key : Any> constructor(
        override val key: Key,
        loadSize: Int,
        placeholdersEnabled: Boolean,
    ) : LoadParams<Key>(
        loadSize = loadSize,
        placeholdersEnabled = placeholdersEnabled,
    )


}

