package com.ustadmobile.door.paging

actual fun <Key: Any, Value: Any> DoorLoadResult<Key, Value>.toLoadResult(): LoadResult<Key, Value> {
    return when(this) {
        is DoorLoadResult.Page -> LoadResult.Page(data, prevKey, nextKey, itemsBefore, itemsAfter)
        is DoorLoadResult.Error -> LoadResult.Error(throwable)
        is DoorLoadResult.Invalid -> LoadResult.Invalid()
    }
}

actual fun <Key: Any, Value: Any> LoadResult<Key, Value>.toDoorLoadResult(): DoorLoadResult<Key, Value> {
    return when(this) {
        is LoadResult.Page -> DoorLoadResult.Page(data, prevKey, nextKey, itemsBefore, itemsAfter)
        is LoadResult.Error -> DoorLoadResult.Error(throwable)
        is LoadResult.Invalid -> DoorLoadResult.Invalid()
    }
}
