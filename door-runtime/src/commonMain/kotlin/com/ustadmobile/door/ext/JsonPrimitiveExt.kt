package com.ustadmobile.door.ext

import com.ustadmobile.door.jdbc.TypesKmp
import kotlinx.serialization.json.JsonPrimitive

fun JsonPrimitive?.toDefaultValIfNull(fieldType: Int) : JsonPrimitive{
    return when {
        this != null -> this
        fieldType == TypesKmp.VARCHAR -> JsonPrimitive(null as String?)
        fieldType == TypesKmp.LONGVARCHAR -> JsonPrimitive(null as String?)
        fieldType == TypesKmp.BOOLEAN -> JsonPrimitive(false)
        else -> JsonPrimitive(0)
    }
}
