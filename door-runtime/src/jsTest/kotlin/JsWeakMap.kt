import com.ustadmobile.door.util.WeakMapJs
import kotlin.test.Test

class JsWeakMapTest {

    @Test
    fun makeMap() {
        val map = WeakMapJs<String, String>()
        println(map)
    }

}