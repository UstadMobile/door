/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.room


/**
 * Marks a method in a [Dao] annotated class as a query method.
 *
 *
 * The value of the annotation includes the query that will be run when this method is called. This
 * query is **verified at compile time** by Room to ensure that it compiles fine against the
 * database.
 *
 *
 * The arguments of the method will be bound to the bind arguments in the SQL statement. See
 * <href></href>="https://www.sqlite.org/c3ref/bind_blob.html">SQLite's binding documentation> for
 * details of bind arguments in SQLite.
 *
 *
 * Room only supports named bind parameter `:name` to avoid any confusion between the
 * method parameters and the query bind parameters.
 *
 *
 * Room will automatically bind the parameters of the method into the bind arguments. This is done
 * by matching the name of the parameters to the name of the bind arguments.
 * <pre>
 * @Query("SELECT * FROM user WHERE user_name LIKE :name AND last_name LIKE :last")
 * public abstract List&lt;User&gt; findUsersByNameAndLastName(String name, String last);
</pre> *
 *
 *
 * As an extension over SQLite bind arguments, Room supports binding a list of parameters to the
 * query. At runtime, Room will build the correct query to have matching number of bind arguments
 * depending on the number of items in the method parameter.
 * <pre>
 * @Query("SELECT * FROM user WHERE uid IN(:userIds)")
 * public abstract List<User> findByIds(int[] userIds);
</User></pre> *
 * For the example above, if the `userIds` is an array of 3 elements, Room will run the
 * query as: `SELECT * FROM user WHERE uid IN(?, ?, ?)` and bind each item in the
 * `userIds` array into the statement.
 *
 *
 * There are 4 types of queries supported in `Query` methods: SELECT, INSERT, UPDATE, and
 * DELETE.
 *
 *
 * For SELECT queries, Room will infer the result contents from the method's return type and
 * generate the code that will automatically convert the query result into the method's return
 * type. For single result queries, the return type can be any java object. For queries that return
 * multiple values, you can use [java.util.List] or `Array`. In addition to these, any
 * query may return [Cursor][android.database.Cursor] or any query result can be wrapped in
 * a [LiveData][androidx.lifecycle.LiveData].
 *
 *
 * **RxJava2** If you are using RxJava2, you can also return `Flowable<T>` or
 * `Publisher<T>` from query methods. Since Reactive Streams does not allow `null`, if
 * the query returns a nullable type, it will not dispatch anything if the value is `null`
 * (like fetching an [Entity] row that does not exist).
 * You can return `Flowable<T[]>` or `Flowable<List<T>>` to workaround this limitation.
 *
 *
 * Both `Flowable<T>` and `Publisher<T>` will observe the database for changes and
 * re-dispatch if data changes. If you want to query the database without observing changes, you can
 * use `Maybe<T>` or `Single<T>`. If a `Single<T>` query returns `null`,
 * Room will throw
 * [EmptyResultSetException][androidx.room.EmptyResultSetException].
 *
 *
 * INSERT queries can return `void` or `long`. If it is a `long`, the value is the
 * SQLite rowid of the row inserted by this query. Note that queries which insert multiple rows
 * cannot return more than one rowid, so avoid such statements if returning `long`.
 *
 *
 * UPDATE or DELETE queries can return `void` or `int`. If it is an `int`,
 * the value is the number of rows affected by this query.
 *
 *
 * You can return arbitrary POJOs from your query methods as long as the fields of the POJO match
 * the column names in the query result.
 * For example, if you have class:
 * <pre>
 * class UserName {
 * public String name;
 * @ColumnInfo(name = "last_name")
 * public String lastName;
 * }
</pre> *
 * You can write a query like this:
 * <pre>
 * @Query("SELECT last_name, name FROM user WHERE uid = :userId LIMIT 1")
 * public abstract UserName findOneUserName(int userId);
</pre> *
 * And Room will create the correct implementation to convert the query result into a
 * `UserName` object. If there is a mismatch between the query result and the fields of the
 * POJO, as long as there is at least 1 field match, Room prints a
 * [RoomWarnings.CURSOR_MISMATCH] warning and sets as many fields as it can.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
@Retention(AnnotationRetention.BINARY)
annotation class Query(
        /**
         * The SQLite query to be run.
         * @return The query to be run.
         */
        val value: String)
