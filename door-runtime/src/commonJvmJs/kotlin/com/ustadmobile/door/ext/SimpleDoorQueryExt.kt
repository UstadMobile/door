package com.ustadmobile.door.ext

import com.ustadmobile.door.DoorQuery
import com.ustadmobile.door.SimpleDoorQuery

fun DoorQuery.copyWithExtraParams(
    sql: String = getSql(),
    extraParams: Array<out Any?> = arrayOf()
) : DoorQuery {
    val existingParams: Array<out Any?> = (this as? SimpleDoorQuery)?.values
        ?: throw IllegalArgumentException("copyWithExtraParmas: must be simpledoorquery")
    val allParams = Array(existingParams.size + extraParams.size) { index ->
        if(index < existingParams.size)
            existingParams[index]
        else
            extraParams[index - existingParams.size]
    }

    return SimpleDoorQuery(sql, allParams)
}

fun DoorQuery.copy(sql: String) : DoorQuery{
    val existingParams: Array<out Any?> = (this as? SimpleDoorQuery)?.values
        ?: throw IllegalArgumentException("copyWithExtraParmas: must be simpledoorquery")
    return SimpleDoorQuery(sql, existingParams)
}
