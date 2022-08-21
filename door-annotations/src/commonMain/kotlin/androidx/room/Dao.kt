package androidx.room

/**
 * Marks the class as a Data Access Object.
 *
 *
 * Data Access Objects are the main classes where you define your database interactions. They can
 * include a variety of query methods.
 *
 *
 * The class marked with `@Dao` should either be an interface or an abstract class. At compile
 * time, Room will generate an implementation of this class when it is referenced by a
 * [Database].
 *
 *
 * An abstract `@Dao` class can optionally have a constructor that takes a [Database]
 * as its only parameter.
 *
 *
 * It is recommended to have multiple `Dao` classes in your codebase depending on the tables
 * they touch.
 *
 * @see Query
 *
 * @see Delete
 *
 * @see Insert
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FILE)
@Retention(AnnotationRetention.BINARY)
annotation class Dao