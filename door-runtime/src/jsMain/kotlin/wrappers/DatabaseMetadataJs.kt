package wrappers

import com.ustadmobile.door.jdbc.DatabaseMetadata
import com.ustadmobile.door.jdbc.ResultSet

class DatabaseMetadataJs(val datasource: SQLiteDatasourceJs): DatabaseMetadata {

    override val databaseProductName: String
        get() = DB_PRODUCT_NAME_SQLITE

    override fun getTables(
        catalog: String?,
        schemaPattern: String?,
        tableNamePattern: String?,
        types: Array<out String>
    ): ResultSet {
        throw Exception("getTables: This can not be used on JS, only for JVM. Call getTablesAsync instead")
    }

    companion object {
        const val DB_PRODUCT_NAME_SQLITE = "SQLite"
    }
}