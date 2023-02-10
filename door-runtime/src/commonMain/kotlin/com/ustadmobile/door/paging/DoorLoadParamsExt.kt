package com.ustadmobile.door.paging

expect fun <Key: Any> DoorLoadParams<Key>.toLoadParams(): LoadParams<Key>

expect fun <Key: Any> LoadParams<Key>.toDoorLoadParams(): DoorLoadParams<Key>
