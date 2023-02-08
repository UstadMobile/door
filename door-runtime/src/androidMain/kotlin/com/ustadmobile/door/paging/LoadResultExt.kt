package com.ustadmobile.door.paging

import androidx.paging.PagingSource.LoadResult as AndroidLoadResult

actual fun <Key: Any, Value: Any> DoorLoadResult<Key, Value>.toLoadResult(): AndroidLoadResult<Key, Value>{
    return when(this) {
        is DoorLoadResult.Page -> AndroidLoadResult.Page(data, prevKey, nextKey, itemsBefore, itemsAfter)
        is DoorLoadResult.Error -> AndroidLoadResult.Error(throwable)
        is DoorLoadResult.Invalid -> AndroidLoadResult.Invalid()
    }
}

actual fun <Key: Any, Value: Any> AndroidLoadResult<Key, Value>.toDoorLoadResult(): DoorLoadResult<Key, Value> {
    return when(this) {
        is AndroidLoadResult.Page -> DoorLoadResult.Page(data, prevKey, nextKey, itemsBefore, itemsAfter)
        is AndroidLoadResult.Error -> DoorLoadResult.Error(throwable)
        is AndroidLoadResult.Invalid -> DoorLoadResult.Invalid()
    }
}
