package androidx.room

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
import androidx.annotation.IntDef
import androidx.annotation.RequiresApi

/**
 * Allows specific customization about the column associated with this field.
 *
 *
 * For example, you can specify a column name for the field or change the column's type affinity.
 */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.BINARY)
annotation class ColumnInfo(
        /**
         * Name of the column in the database. Defaults to the field name if not set.
         *
         * @return Name of the column in the database.
         */
        val name: String = INHERIT_FIELD_NAME,
        /**
         * The type affinity for the column, which will be used when constructing the database.
         *
         *
         * If it is not specified, the value defaults to [.UNDEFINED] and Room resolves it based
         * on the field's type and available TypeConverters.
         *
         *
         * See [SQLite types documentation](https://www.sqlite.org/datatype3.html) for
         * details.
         *
         * @return The type affinity of the column. This is either [.UNDEFINED], [.TEXT],
         * [.INTEGER], [.REAL], or [.BLOB].
         */
        @SQLiteTypeAffinity val typeAffinity: Int = UNDEFINED,
        /**
         * Convenience method to index the field.
         *
         *
         * If you would like to create a composite index instead, see: [Index].
         *
         * @return True if this field should be indexed, false otherwise. Defaults to false.
         */
        val index: Boolean = false,
        /**
         * The collation sequence for the column, which will be used when constructing the database.
         *
         *
         * The default value is [.UNSPECIFIED]. In that case, Room does not add any
         * collation sequence to the column, and SQLite treats it like [.BINARY].
         *
         * @return The collation sequence of the column. This is either [.UNSPECIFIED],
         * [.BINARY], [.NOCASE], [.RTRIM], [.LOCALIZED] or [.UNICODE].
         */
        @Collate val collate: Int = UNSPECIFIED) {
    /**
     * The SQLite column type constants that can be used in [.typeAffinity]
     */
    @IntDef(UNDEFINED, TEXT, INTEGER, REAL, BLOB)
    annotation class SQLiteTypeAffinity

    @IntDef(UNSPECIFIED, BINARY, NOCASE, RTRIM, LOCALIZED, UNICODE)
    annotation class Collate

    companion object {
        /**
         * Constant to let Room inherit the field name as the column name. If used, Room will use the
         * field name as the column name.
         */
        const val INHERIT_FIELD_NAME = "[field-name]"
        /**
         * Undefined type affinity. Will be resolved based on the type.
         *
         * @see .typeAffinity
         */
        const val UNDEFINED = 1
        /**
         * Column affinity constant for strings.
         *
         * @see .typeAffinity
         */
        const val TEXT = 2
        /**
         * Column affinity constant for integers or booleans.
         *
         * @see .typeAffinity
         */
        const val INTEGER = 3
        /**
         * Column affinity constant for floats or doubles.
         *
         * @see .typeAffinity
         */
        const val REAL = 4
        /**
         * Column affinity constant for binary data.
         *
         * @see .typeAffinity
         */
        const val BLOB = 5
        /**
         * Collation sequence is not specified. The match will behave like [.BINARY].
         *
         * @see .collate
         */
        const val UNSPECIFIED = 1
        /**
         * Collation sequence for case-sensitive match.
         *
         * @see .collate
         */
        const val BINARY = 2
        /**
         * Collation sequence for case-insensitive match.
         *
         * @see .collate
         */
        const val NOCASE = 3
        /**
         * Collation sequence for case-sensitive match except that trailing space characters are
         * ignored.
         *
         * @see .collate
         */
        const val RTRIM = 4
        /**
         * Collation sequence that uses system's current locale.
         *
         * @see .collate
         */
        @RequiresApi(21)
        const val LOCALIZED = 5
        /**
         * Collation sequence that uses Unicode Collation Algorithm.
         *
         * @see .collate
         */
        @RequiresApi(21)
        const val UNICODE = 6
    }
}