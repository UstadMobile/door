package androidx.room

import kotlin.reflect.KClass

/*
 * Copyright 2018 The Android Open Source Project
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

/**
 * Marks a method in a [Dao] annotated class as a raw query method where you can pass the
 * query as a [SupportSQLiteQuery][androidx.sqlite.db.SupportSQLiteQuery].
 * <pre>
 * @Dao
 * interface RawDao {
 * @RawQuery
 * User getUserViaQuery(SupportSQLiteQuery query);
 * }
 * SimpleSQLiteQuery query = new SimpleSQLiteQuery("SELECT * FROM User WHERE id = ? LIMIT 1",
 * new Object[]{userId});
 * User user2 = rawDao.getUserViaQuery(query);
</pre> *
 *
 *
 * Room will generate the code based on the return type of the function and failure to
 * pass a proper query will result in a runtime failure or an undefined result.
 *
 *
 * If you know the query at compile time, you should always prefer [Query] since it validates
 * the query at compile time and also generates more efficient code since Room can compute the
 * query result at compile time (e.g. it does not need to account for possibly missing columns in
 * the response).
 *
 *
 * On the other hand, `RawQuery` serves as an escape hatch where you can build your own
 * SQL query at runtime but still use Room to convert it into objects.
 *
 *
 * `RawQuery` methods must return a non-void type. If you want to execute a raw query that
 * does not return any value, use [ RoomDatabase#query][androidx.room.RoomDatabase.query] methods.
 *
 *
 * RawQuery methods can only be used for read queries. For write queries, use
 * [ RoomDatabase.getOpenHelper().getWritableDatabase()][androidx.room.RoomDatabase.getOpenHelper].
 *
 *
 * **Observable Queries:**
 *
 *
 * `RawQuery` methods can return observable types but you need to specify which tables are
 * accessed in the query using the [.observedEntities] field in the annotation.
 * <pre>
 * @Dao
 * interface RawDao {
 * @RawQuery(observedEntities = User.class)
 * LiveData&lt;List&lt;User>> getUsers(SupportSQLiteQuery query);
 * }
 * LiveData&lt;List&lt;User>> liveUsers = rawDao.getUsers(
 * new SimpleSQLiteQuery("SELECT * FROM User ORDER BY name DESC"));
</pre> *
 * **Returning Pojos:**
 *
 *
 * RawQueries can also return plain old java objects, similar to [Query] methods.
 * <pre>
 * public class NameAndLastName {
 * public final String name;
 * public final String lastName;
 *
 * public NameAndLastName(String name, String lastName) {
 * this.name = name;
 * this.lastName = lastName;
 * }
 * }
 *
 * @Dao
 * interface RawDao {
 * @RawQuery
 * NameAndLastName getNameAndLastName(SupportSQLiteQuery query);
 * }
 * NameAndLastName result = rawDao.getNameAndLastName(
 * new SimpleSQLiteQuery("SELECT * FROM User WHERE id = ?", new Object[]{userId}))
 * // or
 * NameAndLastName result = rawDao.getNameAndLastName(
 * new SimpleSQLiteQuery("SELECT name, lastName FROM User WHERE id = ?",
 * new Object[]{userId})))
</pre> *
 *
 *
 * **Pojos with Embedded Fields:**
 *
 *
 * `RawQuery` methods can return pojos that include [Embedded] fields as well.
 * <pre>
 * public class UserAndPet {
 * @Embedded
 * public User user;
 * @Embedded
 * public Pet pet;
 * }
 *
 * @Dao
 * interface RawDao {
 * @RawQuery
 * UserAndPet getUserAndPet(SupportSQLiteQuery query);
 * }
 * UserAndPet received = rawDao.getUserAndPet(
 * new SimpleSQLiteQuery("SELECT * FROM User, Pet WHERE User.id = Pet.userId LIMIT 1"))
</pre> *
 *
 * **Relations:**
 *
 *
 * `RawQuery` return types can also be objects with [Relations][Relation].
 * <pre>
 * public class UserAndAllPets {
 * @Embedded
 * public User user;
 * @Relation(parentColumn = "id", entityColumn = "userId")
 * public List&lt;Pet> pets;
 * }
 *
 * @Dao
 * interface RawDao {
 * @RawQuery
 * List&lt;UserAndAllPets> getUsersAndAllPets(SupportSQLiteQuery query);
 * }
 * List&lt;UserAndAllPets> result = rawDao.getUsersAndAllPets(
 * new SimpleSQLiteQuery("SELECT * FROM users"));
</pre> *
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
@Retention(AnnotationRetention.BINARY)
annotation class RawQuery(
        /**
         * Denotes the list of entities which are accessed in the provided query and should be observed
         * for invalidation if the query is observable.
         *
         *
         * The listed classes should either be annotated with [Entity] or they should reference to
         * at least 1 Entity (via [Embedded] or [Relation]).
         *
         *
         * Providing this field in a non-observable query has no impact.
         * <pre>
         * @Dao
         * interface RawDao {
         * @RawQuery(observedEntities = User.class)
         * LiveData&lt;List&lt;User>> getUsers(String query);
         * }
         * LiveData&lt;List&lt;User>> liveUsers = rawDao.getUsers("select * from User ORDER BY name
         * DESC");
        </pre> *
         *
         * @return List of entities that should invalidate the query if changed.
         */
        val observedEntities: Array<KClass<*>> = arrayOf())
