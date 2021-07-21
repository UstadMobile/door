import com.ustadmobile.door.util.randomUuid
import kotlin.test.Test
import kotlin.test.assertNotNull

class TestKmpUuid {
    @Test
    fun givenExecution_whenRandomUuidRequested_shouldBeGenerated() {
        val uuid = randomUuid()
        assertNotNull(uuid)
    }

}