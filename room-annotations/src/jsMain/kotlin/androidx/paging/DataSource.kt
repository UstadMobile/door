package androidx.paging

actual open class DataSource<Key, Value> {

    actual abstract class Factory<Key, Value> {

        abstract suspend fun getData(offset: Int, limit: Int): List<Value>

    }
}