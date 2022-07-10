package androidx.room

expect abstract class RoomDatabase() {

    class Builder<T: RoomDatabase> {

    }

    abstract fun clearAllTables()

    abstract val invalidationTracker: InvalidationTracker

}