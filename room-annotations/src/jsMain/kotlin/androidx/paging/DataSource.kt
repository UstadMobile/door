package androidx.paging

actual open class DataSource<Key, Value> {

    actual abstract class Factory<Key, Value> {

        //TODO: this needs replaced with something using paging
        //abstract suspend fun getData(offset: Int, limit: Int): List<Value>

    }
}