package com.ustadmobile.door.paging


actual fun <Key: Any> DoorLoadParams<Key>.toLoadParams(): LoadParams<Key> {
    return when(this) {
        is DoorLoadParams.Refresh -> LoadParams.Refresh(key, loadSize, placeholdersEnabled)
        is DoorLoadParams.Append -> LoadParams.Append(key, loadSize, placeholdersEnabled)
        is DoorLoadParams.Prepend -> LoadParams.Prepend(key, loadSize, placeholdersEnabled)
    }
}

actual fun <Key: Any> LoadParams<Key>.toDoorLoadParams(): DoorLoadParams<Key> {
    return when(this) {
        is LoadParams.Refresh -> DoorLoadParams.Refresh(key, loadSize, placeholdersEnabled)
        is LoadParams.Append -> DoorLoadParams.Append(key, loadSize, placeholdersEnabled)
        is LoadParams.Prepend ->DoorLoadParams.Prepend(key, loadSize, placeholdersEnabled)
    }
}
