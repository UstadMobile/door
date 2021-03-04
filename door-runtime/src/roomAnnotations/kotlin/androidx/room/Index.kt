/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * Declares an index on an Entity.
 * see: [SQLite Index Documentation](https://sqlite.org/lang_createindex.html)
 *
 *
 * Adding an index usually speeds up your select queries but will slow down other queries like
 * insert or update. You should be careful when adding indices to ensure that this additional cost
 * is worth the gain.
 *
 *
 * There are 2 ways to define an index in an [Entity]. You can either set
 * [ColumnInfo.index] property to index individual fields or define composite indices via
 * [Entity.indices].
 *
 *
 * If an indexed field is embedded into another Entity via [Embedded], it is **NOT**
 * added as an index to the containing [Entity]. If you want to keep it indexed, you must
 * re-declare it in the containing [Entity].
 *
 *
 * Similarly, if an [Entity] extends another class, indices from the super classes are
 * **NOT** inherited. You must re-declare them in the child [Entity] or set
 * [Entity.inheritSuperIndices] to `true`.
 */
@Target
@Retention(AnnotationRetention.BINARY)
annotation class Index(
        /**
         * List of column names in the Index.
         *
         *
         * The order of columns is important as it defines when SQLite can use a particular index.
         * See [SQLite documentation](https://www.sqlite.org/optoverview.html) for details on
         * index usage in the query optimizer.
         *
         * @return The list of column names in the Index.
         */
        vararg val value: String,
        /**
         * Name of the index. If not set, Room will set it to the list of columns joined by '_' and
         * prefixed by "index_${tableName}". So if you have a table with name "Foo" and with an index
         * of {"bar", "baz"}, generated index name will be  "index_Foo_bar_baz". If you need to specify
         * the index in a query, you should never rely on this name, instead, specify a name for your
         * index.
         *
         * @return The name of the index.
         */
        val name: String = "",
        /**
         * If set to true, this will be a unique index and any duplicates will be rejected.
         *
         * @return True if index is unique. False by default.
         */
        val unique: Boolean = false)
