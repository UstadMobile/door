package com.ustadmobile.door.paging

actual fun <Key: Any, Value: Any> DoorLoadResult<Key, Value>.toLoadResult(): LoadResult<Key, Value> {
    TODO()
}

actual fun <Key: Any, Value: Any> LoadResult<Key, Value>.toDoorLoadResult(): DoorLoadResult<Key, Value> {
    TODO()
}
