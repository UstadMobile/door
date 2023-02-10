package com.ustadmobile.door.paging
import androidx.paging.PagingSource.LoadParams as AndroidLoadParams

actual fun <Key: Any> DoorLoadParams<Key>.toLoadParams(): LoadParams<Key> {
    return when(this) {
        is DoorLoadParams.Refresh -> AndroidLoadParams.Refresh(key, loadSize, placeholdersEnabled)
        is DoorLoadParams.Append -> AndroidLoadParams.Append(key, loadSize, placeholdersEnabled)
        is DoorLoadParams.Prepend -> AndroidLoadParams.Prepend(key, loadSize, placeholdersEnabled)
    }
}

actual fun <Key: Any> AndroidLoadParams<Key>.toDoorLoadParams(): DoorLoadParams<Key> {
    return when(this) {
        is AndroidLoadParams.Refresh -> DoorLoadParams.Refresh(key, loadSize, placeholdersEnabled)
        is AndroidLoadParams.Append -> DoorLoadParams.Append(key, loadSize, placeholdersEnabled)
        is AndroidLoadParams.Prepend -> DoorLoadParams.Prepend(key, loadSize, placeholdersEnabled)
    }
}
