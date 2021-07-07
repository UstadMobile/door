import com.ustadmobile.door.ext.doorIdentityHashCode
import kotlin.test.Test
import kotlin.test.assertNotEquals

data class DummyDataClass(var prop: Any)

class TestAnyExt {
    @Test
    fun givenStringContent_whenHashCodeRequested_shouldBeProvided() {
        val hashCode = "Sample content".doorIdentityHashCode
        assertNotEquals(hashCode, 0)
    }

    @Test
    fun givenIntegerContent_whenHashCodeRequested_shouldBeProvided() {
        val hashCode = 12.doorIdentityHashCode
        assertNotEquals(hashCode, 0)
    }

    @Test
    fun givenDataClassAsContent_whenHashCodeRequested_shouldBeProvided() {
        val hashCode = DummyDataClass("hello").doorIdentityHashCode
        assertNotEquals(hashCode, 0)
    }

    @Test
    fun givenListAsContent_whenHashCodeRequested_shouldBeProvided() {
        val hashCode = listOf(1,"Data").doorIdentityHashCode
        assertNotEquals(hashCode, 0)
    }
}