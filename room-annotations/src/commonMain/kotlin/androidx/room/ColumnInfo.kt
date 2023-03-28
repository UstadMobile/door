package androidx.room

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
        @Collate val collate: Int = UNSPECIFIED,

        /**
         * The default value for this column.
         * <pre>
         *   {@literal @}ColumnInfo(defaultValue = "No name")
         *   public String name;
         *
         *  {@literal @}ColumnInfo(defaultValue = "0")
         *   public int flag;
         * </pre>
         * <p>
         * Note that the default value you specify here will <em>NOT</em> be used if you simply
         * insert the {@link Entity} with {@link Insert @Insert}. In that case, any value assigned in
         * Java/Kotlin will be used. Use {@link Query @Query} with an <code>INSERT</code> statement
         * and skip this column there in order to use this default value.
         * </p>
         * <p>
         * NULL, CURRENT_TIMESTAMP and other SQLite constant values are interpreted as such. If you want
         * to use them as strings for some reason, surround them with single-quotes.
         * </p>
         * <pre>
         *   {@literal @}ColumnInfo(defaultValue = "NULL")
         *   {@literal @}Nullable
         *   public String description;
         *
         *   {@literal @}ColumnInfo(defaultValue = "'NULL'")
         *   {@literal @}NonNull
         *   public String name;
         * </pre>
         * <p>
         * You can also use constant expressions by surrounding them with parentheses.
         * </p>
         * <pre>
         *   {@literal @}CoumnInfo(defaultValue = "('Created at' || CURRENT_TIMESTAMP)")
         *   public String notice;
         * </pre>
         *
         * @return The default value for this column.
         * @see #VALUE_UNSPECIFIED
         */
        val defaultValue: String = VALUE_UNSPECIFIED) {
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

        const val VALUE_UNSPECIFIED = "[value-unspecified]"
    }
}