package androidx.room

/**
 * Marks a method in a [Dao] annotated class as an insert method.
 *
 *
 * The implementation of the method will insert its parameters into the database.
 *
 *
 * All of the parameters of the Insert method must either be classes annotated with [Entity]
 * or collections/array of it.
 *
 *
 * Example:
 * <pre>
 * @Dao
 * public interface MyDao {
 * @Insert(onConflict = OnConflictStrategy.REPLACE)
 * public void insertUsers(User... users);
 * @Insert
 * public void insertBoth(User user1, User user2);
 * @Insert
 * public void insertWithFriends(User user, List&lt;User&gt; friends);
 * }
</pre> *
 *
 * @see Update
 *
 * @see Delete
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
@Retention(AnnotationRetention.BINARY)
annotation class Insert(
        /**
         * What to do if a conflict happens.
         *
         *
         * Use [OnConflictStrategy.ABORT] (default) to roll back the transaction on conflict.
         * Use [OnConflictStrategy.REPLACE] to replace the existing rows with the new rows.
         * Use [OnConflictStrategy.IGNORE] to keep the existing rows.
         *
         * @return How to handle conflicts. Defaults to [OnConflictStrategy.ABORT].
         */
        @OnConflictStrategy
        val onConflict: Int = OnConflictStrategy.ABORT)