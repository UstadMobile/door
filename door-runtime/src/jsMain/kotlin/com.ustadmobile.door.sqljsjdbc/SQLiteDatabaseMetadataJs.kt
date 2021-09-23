package com.ustadmobile.door.sqljsjdbc

import com.ustadmobile.door.jdbc.DatabaseMetadata
import com.ustadmobile.door.jdbc.ResultSet
import kotlin.text.Regex.Companion.escape

class SQLiteDatabaseMetadataJs(val datasource: SQLiteDatasourceJs): DatabaseMetadata {

    override fun getTables(
        catalog: String?,
        schemaPattern: String?,
        tableNamePattern: String?,
        types: Array<out String>
    ): ResultSet {
        throw Exception("getTables: This can not be used on JS, only for JVM. Call getTablesAsync instead")
    }

    /**
     * List all tables from the database, this implementation was adapter from SQLiteJDBC
     * @see https://github.com/xerial/sqlite-jdbc/blob/master/src/main/java/org/sqlite/jdbc3/JDBC3DatabaseMetaData.java
     */
    suspend fun getTablesAsync(catalog: String?, schemaPattern: String?, tableNamePattern: String?,
                               types: Array<out String>): ResultSet {

        val tblNamePattern = if (tableNamePattern == null || "" == tableNamePattern) "%" else escape(tableNamePattern)

        var sql = """
            SELECT NULL AS TABLE_CAT,
                   NULL AS TABLE_SCHEM,
                   NAME AS TABLE_NAME,
                   TYPE AS TABLE_TYPE,
                   NULL AS REMARKS,
                   NULL AS TYPE_CAT,
                   NULL AS TYPE_SCHEM,
                   NULL AS TYPE_NAME,
                   NULL AS SELF_REFERENCING_COL_NAME,
                   NULL AS REF_GENERATION 
            FROM 
                   (SELECT NAME,
                        UPPER(TYPE) AS TYPE 
                    FROM sqlite_master
                    WHERE NAME NOT LIKE 'sqlite\_%' ESCAPE '\'
                        AND UPPER(TYPE) IN ('TABLE', 'VIEW') 
                    UNION ALL
                        SELECT NAME, 'GLOBAL TEMPORARY' AS TYPE
                        FROM sqlite_temp_master
                        UNION ALL 
                            SELECT NAME,'SYSTEM TABLE' AS TYPE
                            FROM sqlite_master
                            WHERE NAME LIKE 'sqlite\_%' ESCAPE '\') 
            WHERE TABLE_NAME LIKE '$tblNamePattern' AND TABLE_TYPE IN (
        """.trimIndent()


        if (types.isNullOrEmpty()) {
            sql += "'TABLE','VIEW'"
        } else {
            sql += "'${types[0].uppercase()}'"
            for (i in 1 until types.size) {
                sql += ",'${types[i].uppercase()}'"
            }
        }

        sql += ") ORDER BY TABLE_TYPE, TABLE_NAME"
        return datasource.sendQuery(sql)
    }
}