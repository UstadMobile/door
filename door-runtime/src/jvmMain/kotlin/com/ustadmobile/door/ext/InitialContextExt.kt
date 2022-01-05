package com.ustadmobile.door.ext

import org.sqlite.SQLiteConfig
import org.sqlite.SQLiteDataSource
import java.io.File
import javax.naming.InitialContext

/**
 * For the given JNDI context bind an SQLite Datasource there if it is not already bound
 */
fun InitialContext.bindNewSqliteDataSourceIfNotExisting(
    dbName: String,
    isPrimary: Boolean = false,
    sqliteDir: File = File("build")
) {
    try {
        val existingDs = lookup("java:/comp/env/jdbc/$dbName")
        return
    }catch(e: Exception) {
        //not bound yet...
    }


    val dbJndiName = "java:/comp/env/jdbc/$dbName"

    try {
        val jdbcBind = lookup("java:/comp/env/jdbc")
    }catch(e: Exception) {
        createSubcontext("java:/comp/env/jdbc")
    }

    try {
        val dataSource = this.lookup(dbJndiName)
    }catch(e: Exception) {
        val newDatasource = SQLiteDataSource(SQLiteConfig().apply{
            setJournalMode(SQLiteConfig.JournalMode.WAL)
            setBusyTimeout(30000)
            setSynchronous(SQLiteConfig.SynchronousMode.OFF)
            enableRecursiveTriggers(true)
        })

        sqliteDir.takeIf { !it.exists() }?.mkdirs()
        val sqliteFile = File(sqliteDir, "$dbName.sqlite")
        newDatasource.url = "jdbc:sqlite:${sqliteFile.absolutePath}"
        bind(dbJndiName, newDatasource)
    }
}