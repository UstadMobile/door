package com.ustadmobile.door.paging

actual class PagingState<Key: Any, Value: Any>  {

    actual val anchorPosition: Int? = null

    actual fun closestItemToPosition(anchorPosition: Int): Value? {
        TODO()
    }

}