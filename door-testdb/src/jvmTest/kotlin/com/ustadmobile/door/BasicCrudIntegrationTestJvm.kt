package com.ustadmobile.door

import com.ustadmobile.door.test.BasicCrudIntegrationTest.Companion.runExampleDbTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Two tests that must run using synchronous functions (not supported on JS) live here
 */
class BasicCrudIntegrationTestJvm {

    @Test
    fun givenQueryPrimitiveReturnTypeNotNullable_whenNoRowsReturned_thenShouldReturnDefaultValue() = runExampleDbTest {
        assertEquals(0, exampleDb2.exampleDao2().findSingleNotNullablePrimitive(Int.MAX_VALUE),
            message = "Primitive not nullable type query will return default value when no rows match")
    }

    @Test
    fun givenQueryPrimitiveReturnTypeNullable_whenNoRowsReturn_thenShouldReturnNull() = runExampleDbTest{
        assertNull(exampleDb2.exampleDao2().findSingleNullablePrimitive(Int.MAX_VALUE),
            message = "Primitive nullable type query will return null when no rows match")
    }

}