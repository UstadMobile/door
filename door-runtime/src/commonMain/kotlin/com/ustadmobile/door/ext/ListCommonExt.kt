package com.ustadmobile.door.ext

/**
 * Splits a list into sublists whilst maintaining the same order. For example where we want to split a list of events by
 * tableId. We want to use a single preparedStatement where we have the same entity type in a row, and run the insert
 * in the same order overall.
 *
 * E.g. if there is a list containing [T1, T1, T2, T2, T1], this will output [[T1, T1], [T2, T2], [T1]]
 */
inline fun <T, K> Iterable<T>.runningSplitBy(
    key: (T) -> K
) : List<List<T>> {
    val result = mutableLinkedListOf<MutableList<T>>()
    var lastItemKey: K? = null
    var lastList: MutableList<T>? = null

    for(item in this) {
        val itemKey = key(item)
        if(lastItemKey != null && lastList != null && itemKey == lastItemKey) {
            lastList.add(item)
        }else {
            val newList = mutableLinkedListOf(item)
            result.add(newList)
            lastList = newList
        }
        lastItemKey = itemKey
    }

    return result.toList()
}
