package com.ustadmobile.door.paging

sealed class DoorLoadParams<Key: Any>(
    /**
     * Requested number of items to load.
     *
     * Note: It is valid for [PagingSource.load] to return a [LoadResult] that has a different
     * number of items than the requested load size.
     */
    public val loadSize: Int,
    /**
     * From [PagingConfig.enablePlaceholders], true if placeholders are enabled and the load
     * request for this [LoadParams] should populate [LoadResult.Page.itemsBefore] and
     * [LoadResult.Page.itemsAfter] if possible.
     */
    public val placeholdersEnabled: Boolean,
) {

    /**
     * Key for the page to be loaded.
     *
     * [key] can be `null` only if this [LoadParams] is [Refresh], and either no `initialKey`
     * is provided to the [Pager] or [PagingSource.getRefreshKey] from the previous
     * [PagingSource] returns `null`.
     *
     * The value of [key] is dependent on the type of [LoadParams]:
     *  * [Refresh]
     *      * On initial load, the nullable `initialKey` passed to the [Pager].
     *      * On subsequent loads due to invalidation or refresh, the result of
     *      [PagingSource.getRefreshKey].
     *  * [Prepend] - [LoadResult.Page.prevKey] of the loaded page at the front of the list.
     *  * [Append] - [LoadResult.Page.nextKey] of the loaded page at the end of the list.
     */
    public abstract val key: Key?


    /**
     * Params for an initial load request on a [PagingSource] from [PagingSource.load] or a
     * refresh triggered by [invalidate].
     */
    public class Refresh<Key : Any> constructor(
        override val key: Key?,
        loadSize: Int,
        placeholdersEnabled: Boolean,
    ) : DoorLoadParams<Key>(
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
    ) : DoorLoadParams<Key>(
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
    ) : DoorLoadParams<Key>(
        loadSize = loadSize,
        placeholdersEnabled = placeholdersEnabled,
    )

}
