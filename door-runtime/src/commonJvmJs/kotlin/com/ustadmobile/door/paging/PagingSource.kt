package com.ustadmobile.door.paging

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

actual sealed class LoadResult<Key : Any, Value : Any>


actual sealed class LoadParams<Key : Any> actual constructor(
    /**
     * Requested number of items to load.
     *
     * Note: It is valid for [PagingSource.load] to return a [LoadResult] that has a different
     * number of items than the requested load size.
     */
    loadSize: Int,
    /**
     * From [PagingConfig.enablePlaceholders], true if placeholders are enabled and the load
     * request for this [LoadParams] should populate [LoadResult.Page.itemsBefore] and
     * [LoadResult.Page.itemsAfter] if possible.
     */
    placeholdersEnabled: Boolean,
) {

    actual abstract val key: Key?

}

