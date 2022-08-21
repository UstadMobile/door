package androidx.room

import kotlin.reflect.KClass

/**
 * Marks a class as a RoomDatabase.
 *
 *
 * The class should be an abstract class and extend
 * [RoomDatabase][androidx.room.RoomDatabase].
 *
 *
 * You can receive an implementation of the class via
 * [Room.databaseBuilder][androidx.room.Room.databaseBuilder] or
 * [Room.inMemoryDatabaseBuilder][androidx.room.Room.inMemoryDatabaseBuilder].
 *
 *
 * <pre>
 * // User and Book are classes annotated with @Entity.
 * @Database(version = 1, entities = {User.class, Book.class})
 * abstract class AppDatabase extends RoomDatabase {
 * // BookDao is a class annotated with @Dao.
 * abstract public BookDao bookDao();
 * // UserDao is a class annotated with @Dao.
 * abstract public UserDao userDao();
 * // UserBookDao is a class annotated with @Dao.
 * abstract public UserBookDao userBookDao();
 * }
</pre> *
 * The example above defines a class that has 2 tables and 3 DAO classes that are used to access it.
 * There is no limit on the number of [Entity] or [Dao] classes but they must be unique
 * within the Database.
 *
 *
 * Instead of running queries on the database directly, you are highly recommended to create
 * [Dao] classes. Using Dao classes will allow you to abstract the database communication in
 * a more logical layer which will be much easier to mock in tests (compared to running direct
 * sql queries). It also automatically does the conversion from `Cursor` to your application
 * classes so you don't need to deal with lower level database APIs for most of your data access.
 *
 *
 * Room also verifies all of your queries in [Dao] classes while the application is being
 * compiled so that if there is a problem in one of the queries, you will be notified instantly.
 * @see Dao
 *
 * @see Entity
 *
 * @see androidx.room.RoomDatabase RoomDatabase
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FILE)

/*
 This should be AnnotationRuntime.BINARY - however a Kotlin compiler bug will cause the compilation
 to fail if this is the case. Using AnnotationRuntime.SOURCE does not affect our compilation, as
 the annotation processor runs on the same module containing the DAO itself.

 See:
 https://youtrack.jetbrains.com/issue/KT-28927
 */
@Retention(AnnotationRetention.SOURCE)
annotation class Database(
        /**
         * The list of entities included in the database. Each entity turns into a table in the
         * database.
         *
         * @return The list of entities in the database.
         */
        val entities: Array<KClass<*>>,
        /**
         * The list of database views included in the database. Each class turns into a view in the
         * database.
         *
         * @return The list of database views.
         */
        val views: Array<KClass<*>> = arrayOf(),
        /**
         * The database version.
         *
         * @return The database version.
         */
        val version: Int,
        /**
         * You can set annotation processor argument (`room.schemaLocation`)
         * to tell Room to export the schema into a folder. Even though it is not mandatory, it is a
         * good practice to have version history in your codebase and you should commit that file into
         * your version control system (but don't ship it with your app!).
         *
         *
         * When `room.schemaLocation` is set, Room will check this variable and if it is set to
         * `true`, its schema will be exported into the given folder.
         *
         *
         * `exportSchema` is `true` by default but you can disable it for databases when
         * you don't want to keep history of versions (like an in-memory only database).
         *
         * @return Whether the schema should be exported to the given folder when the
         * `room.schemaLocation` argument is set. Defaults to `true`.
         */
        val exportSchema: Boolean = true)