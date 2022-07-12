package androidx.room

expect abstract class RoomDatabase() {

    open class Builder<T: RoomDatabase> {

    }

    abstract fun clearAllTables()

    abstract val invalidationTracker: InvalidationTracker

}