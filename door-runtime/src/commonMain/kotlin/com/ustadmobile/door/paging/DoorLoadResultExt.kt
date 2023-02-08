package com.ustadmobile.door.paging

/**
 * Convert the given DoorLoadResult to the platform specific type.
 */
expect fun <Key: Any, Value: Any> DoorLoadResult<Key, Value>.toLoadResult(): LoadResult<Key, Value>

expect fun <Key: Any, Value: Any> LoadResult<Key, Value>.toDoorLoadResult(): DoorLoadResult<Key, Value>
