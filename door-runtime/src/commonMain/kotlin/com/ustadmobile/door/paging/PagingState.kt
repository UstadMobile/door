package com.ustadmobile.door.paging

expect class PagingState<Key: Any, Value: Any> {

    val anchorPosition: Int?

    fun closestItemToPosition(anchorPosition: Int): Value?

}