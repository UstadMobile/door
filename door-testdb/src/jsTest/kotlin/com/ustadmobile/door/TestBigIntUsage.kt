package com.ustadmobile.door

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals

class TestBigIntUsage {

    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())
    @Test
    fun givenLongInJsonArray_whenDecoded_shouldBeEqualToOriginalValue() = coroutineScope.promise {
        val jsonStr = """[{"big": ${Long.MAX_VALUE}}]"""
        val jsonArray = Json.decodeFromString(JsonArray.serializer(), jsonStr)
        assertEquals(Long.MAX_VALUE, jsonArray.get(0).jsonObject.get("big")?.jsonPrimitive?.long)
    }

}
