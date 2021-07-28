package wrappers

interface DatabaseExportToIndexedDbCallback {

    suspend fun onExport(datasource: SQLiteDatasourceJs)
}