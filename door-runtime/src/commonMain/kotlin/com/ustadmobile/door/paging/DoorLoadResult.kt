package com.ustadmobile.door.paging

/**
 * This class is really just a copy of the Android source code. We can't use expect/actual directly because
 * it is sealed.
 *
 * See
 * https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:paging/paging-common/src/main/kotlin/androidx/paging/PagingSource.kt?q=file:androidx%2Fpaging%2FPagingSource.kt%20class:androidx.paging.PagingSource&ss=androidx%2Fplatform%2Fframeworks%2Fsupport
 *
 */
sealed class DoorLoadResult<Key: Any, Value: Any> {

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
    ) : DoorLoadResult<Key, Value>() {
        override fun toString(): String {
            return """LoadResult.Error(
                |   throwable: $throwable
                |) """.trimMargin()
        }
    }

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
    public class Invalid<Key : Any, Value : Any> : DoorLoadResult<Key, Value>() {
        override fun toString(): String {
            return "LoadResult.Invalid"
        }
    }

    /**
     * Success result object for [PagingSource.load].
     *
     * As a convenience, iterating on this object will iterate through its loaded [data].
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
         * Count of items before the loaded data. Must be implemented if
         * [jumping][PagingSource.jumpingSupported] is enabled. Optional otherwise.
         */
        val itemsBefore: Int = COUNT_UNDEFINED,
        /**
         * Count of items after the loaded data. Must be implemented if
         * [jumping][PagingSource.jumpingSupported] is enabled. Optional otherwise.
         */
        val itemsAfter: Int = COUNT_UNDEFINED
    ) : DoorLoadResult<Key, Value>(), Iterable<Value> {

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

        override fun iterator(): Iterator<Value> {
            return data.listIterator()
        }

        override fun toString(): String {
            return """LoadResult.Page(
                |   data size: ${data.size}
                |   first Item: ${data.firstOrNull()}
                |   last Item: ${data.lastOrNull()}
                |   nextKey: $nextKey
                |   prevKey: $prevKey
                |   itemsBefore: $itemsBefore
                |   itemsAfter: $itemsAfter
                |) """.trimMargin()
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

