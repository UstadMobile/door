package androidx.room

/**
 * Marks a method in a [Dao] annotated class as an update method.
 *
 *
 * The implementation of the method will update its parameters in the database if they already
 * exists (checked by primary keys). If they don't already exists, this option will not change the
 * database.
 *
 *
 * All of the parameters of the Update method must either be classes annotated with [Entity]
 * or collections/array of it.
 *
 * @see Insert
 *
 * @see Delete
 */
annotation class Update(
        /**
         * What to do if a conflict happens.
         * @see [SQLite conflict documentation](https://sqlite.org/lang_conflict.html)
         *
         *
         * @return How to handle conflicts. Defaults to [OnConflictStrategy.ABORT].
         */
        @OnConflictStrategy
        val onConflict: Int = OnConflictStrategy.ABORT)