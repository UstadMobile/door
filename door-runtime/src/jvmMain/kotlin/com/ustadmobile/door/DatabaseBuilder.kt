package com.ustadmobile.door

import java.lang.IllegalStateException
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import java.util.*
import javax.naming.InitialContext
import javax.naming.NamingException
import javax.sql.DataSource
import kotlin.reflect.KClass


actual class DatabaseBuilder<T: DoorDatabase>(private var context: Any, private var dbClass: KClass<T>, private var dbName: String){

    private val callbacks = mutableListOf<DoorDatabaseCallback>()

    private val migrationList = mutableListOf<DoorMigration>()

    actual companion object {
        actual fun <T : DoorDatabase> databaseBuilder(context: Any, dbClass: KClass<T>, dbName: String): DatabaseBuilder<T>
            = DatabaseBuilder(context, dbClass, dbName)
    }

    @Suppress("UNCHECKED_CAST")
    actual fun build(): T {
        val iContext = InitialContext()
        val dataSource = iContext.lookup("java:/comp/env/jdbc/${dbName}") as DataSource
        val dbImplClass = Class.forName("${dbClass.java.canonicalName}_JdbcKt") as Class<T>

        val doorDb = if(SyncableDoorDatabase::class.java.isAssignableFrom(dbImplClass)) {
            var isMaster = false
            try {
                val isMasterObj = iContext.lookup("java:/comp/env/doordb/$dbName/master")
                isMaster = if(isMasterObj != null && isMasterObj is Boolean) { isMasterObj } else { false }
            }catch(namingException: NamingException) {
                System.err.println("Warning: could not check if $dbName is master or not, assuming false")
            }

            dbImplClass.getConstructor(DataSource::class.java, Boolean::class.javaPrimitiveType)
                    .newInstance(dataSource, isMaster)
        }else {
            dbImplClass.getConstructor(DataSource::class.java).newInstance(dataSource)
        }

        if(!doorDb.tableNames.any {it.toLowerCase(Locale.ROOT) == DoorDatabase.DBINFO_TABLENAME}) {
            doorDb.createAllTables()
            callbacks.forEach { it.onCreate(doorDb.sqlDatabaseImpl) }
        }else {
            var sqlCon = null as Connection?
            var stmt = null as Statement?
            var resultSet = null as ResultSet?

            var currentDbVersion = -1
            try {
                sqlCon = dataSource.connection
                stmt = sqlCon.createStatement()
                resultSet = stmt.executeQuery("SELECT dbVersion FROM _doorwayinfo")
                if(resultSet.next())
                    currentDbVersion = resultSet.getInt(1)
            }catch(e: SQLException) {
                throw e
            }finally {
                resultSet?.close()
                stmt?.close()
                sqlCon?.close()
            }

            while(currentDbVersion < doorDb.dbVersion) {
                val nextMigration = migrationList.filter { it.startVersion == currentDbVersion}
                        .maxBy { it.endVersion }
                if(nextMigration != null) {
                    nextMigration.migrate(doorDb.sqlDatabaseImpl)
                    currentDbVersion = nextMigration.endVersion
                    doorDb.sqlDatabaseImpl.execSQL("UPDATE _doorwayinfo SET dbVersion = $currentDbVersion")
                }else {
                    throw IllegalStateException("Need to migrate to version " +
                            "${doorDb.dbVersion} from $currentDbVersion - could not find next migration")
                }
            }
        }

        callbacks.forEach { it.onOpen(doorDb.sqlDatabaseImpl)}

        return if(doorDb is SyncableDoorDatabase) {
            doorDb.wrap(dbClass as KClass<SyncableDoorDatabase>) as T
        }else {
            doorDb
        }
    }

    actual fun addCallback(callback: DoorDatabaseCallback) : DatabaseBuilder<T>{
        callbacks.add(callback)

        return this
    }

    actual fun addMigrations(vararg migrations: DoorMigration): DatabaseBuilder<T> {
        migrationList.addAll(migrations)
        return this
    }

}