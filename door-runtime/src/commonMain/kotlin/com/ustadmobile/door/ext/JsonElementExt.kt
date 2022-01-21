package com.ustadmobile.door.ext

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonPrimitive

val JsonElement.jsonNullableString: String?
    get() = if(this is JsonNull) {
        null
    }else {
        this.jsonPrimitive.content
    }
