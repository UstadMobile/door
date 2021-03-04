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
