package androidx.room
/**
 * Marks a method in a [Dao] class as a transaction method.
 *
 *
 * When used on a non-abstract method of an abstract [Dao] class,
 * the derived implementation of the method will execute the super method in a database transaction.
 * All the parameters and return types are preserved. The transaction will be marked as successful
 * unless an exception is thrown in the method body.
 *
 *
 * Example:
 * <pre>
 * @Dao
 * public abstract class ProductDao {
 * @Insert
 * public abstract void insert(Product product);
 * @Delete
 * public abstract void delete(Product product);
 * @Transaction
 * public void insertAndDeleteInTransaction(Product newProduct, Product oldProduct) {
 * // Anything inside this method runs in a single transaction.
 * insert(newProduct);
 * delete(oldProduct);
 * }
 * }
</pre> *
 *
 *
 * When used on a [Query] method that has a `Select` statement, the generated code for
 * the Query will be run in a transaction. There are 2 main cases where you may want to do that:
 *
 *  1. If the result of the query is fairly big, it is better to run it inside a transaction
 * to receive a consistent result. Otherwise, if the query result does not fit into a single
 * [CursorWindow][android.database.CursorWindow], the query result may be corrupted due to
 * changes in the database in between cursor window swaps.
 *  1. If the result of the query is a Pojo with [Relation] fields, these fields are
 * queried separately. To receive consistent results between these queries, you probably want
 * to run them in a single transaction.
 *
 * Example:
 * <pre>
 * class ProductWithReviews extends Product {
 * @Relation(parentColumn = "id", entityColumn = "productId", entity = Review.class)
 * public List&lt;Review> reviews;
 * }
 * @Dao
 * public interface ProductDao {
 * @Transaction @Query("SELECT * from products")
 * public List&lt;ProductWithReviews> loadAll();
 * }
</pre> *
 * If the query is an async query (e.g. returns a [LiveData][android.arch.lifecycle.LiveData]
 * or RxJava Flowable, the transaction is properly handled when the query is run, not when the
 * method is called.
 *
 *
 * Putting this annotation on an [Insert], [Update] or [Delete] method has no
 * impact because they are always run inside a transaction. Similarly, if it is annotated with
 * [Query] but runs an update or delete statement, it is automatically wrapped in a
 * transaction.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
@Retention(AnnotationRetention.BINARY)
annotation class Transaction
