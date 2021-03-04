package androidx.room

/**
 * Marks a method in a [Dao] annotated class as a delete method.
 *
 *
 * The implementation of the method will delete its parameters from the database.
 *
 *
 * All of the parameters of the Delete method must either be classes annotated with [Entity]
 * or collections/array of it.
 *
 *
 * Example:
 * <pre>
 * @Dao
 * public interface MyDao {
 * @Delete
 * public void deleteUsers(User... users);
 * @Delete
 * public void deleteAll(User user1, User user2);
 * @Delete
 * public void deleteWithFriends(User user, List&lt;User&gt; friends);
 * }
</pre> *
 *
 * @see Insert
 *
 * @see Query
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
@Retention(AnnotationRetention.BINARY)
annotation class Delete
