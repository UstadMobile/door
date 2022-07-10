package com.ustadmobile.door

import androidx.room.RoomDatabase
import com.ustadmobile.door.DoorConstants.DBINFO_TABLENAME
import com.ustadmobile.door.attachments.AttachmentFilter
import com.ustadmobile.door.ext.dbType
import com.ustadmobile.door.ext.doorDatabaseMetadata
import com.ustadmobile.door.ext.wrap
import com.ustadmobile.door.migration.DoorMigration
import com.ustadmobile.door.migration.DoorMigrationAsync
import com.ustadmobile.door.migration.DoorMigrationStatementList
import com.ustadmobile.door.migration.DoorMigrationSync
import com.ustadmobile.door.util.PostgresChangeTracker
import kotlinx.coroutines.runBlocking
import java.io.File
import java.lang.IllegalStateException
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import javax.naming.InitialContext
import javax.sql.DataSource
import kotlin.reflect.KClass
import com.ustadmobile.door.jdbc.ext.useResults
import com.ustadmobile.door.jdbc.ext.mapRows


@Suppress("unused") //This is used as an API
class DatabaseBuilder<T: RoomDatabase> internal constructor(
    private var context: Any,
    private var dbClass: KClass<T>,
    private var dbName: String,
    private var attachmentDir: File? = null,
    private var attachmentFilters: List<AttachmentFilter> = mutableListOf(),
    private var queryTimeout: Int = PreparedStatementConfig.STATEMENT_DEFAULT_TIMEOUT_SECS,
){

    private val callbacks = mutableListOf<DoorDatabaseCallback>()

    private val migrationList = mutableListOf<DoorMigration>()

    companion object {
        fun <T : RoomDatabase> databaseBuilder(
            context: Any,
            dbClass: KClass<T>,
            dbName: String,
            attachmentDir: File? = null,
            attachmentFilters: List<AttachmentFilter> = listOf(),
            queryTimeout: Int = PreparedStatementConfig.STATEMENT_DEFAULT_TIMEOUT_SECS,
        ): DatabaseBuilder<T> = DatabaseBuilder(context, dbClass, dbName, attachmentDir, attachmentFilters,
            queryTimeout)
    }

    @Suppress("UNCHECKED_CAST")
    fun build(): T {
        val iContext = InitialContext()
        val dataSource = iContext.lookup("java:/comp/env/jdbc/${dbName}") as DataSource
        val dbImplClass = Class.forName("${dbClass.java.canonicalName}_JdbcKt") as Class<T>

        val doorDb = dbImplClass.getConstructor(RoomDatabase::class.java, DataSource::class.java,
                String::class.java, File::class.java, List::class.java, Int::class.javaPrimitiveType)
            .newInstance(null, dataSource, dbName, attachmentDir, attachmentFilters, queryTimeout)

        val connection = dataSource.connection
        val sqlDatabase = DoorSqlDatabaseConnectionImpl(connection)

        val tableNames: List<String> = connection.metaData.getTables(null, null, "%", arrayOf("TABLE")).useResults { tableResult ->
            tableResult.mapRows { it.getString("TABLE_NAME") ?: "" }
        }

        if(!tableNames.any {it.lowercase() == DBINFO_TABLENAME}) {
            //Do this directly to avoid conflicts with the change tracking systems before tables have been created
            dataSource.connection.use { con ->
                con.createStatement().use { stmt ->
                    doorDb.createAllTables().forEach { sql ->
                        stmt.executeUpdate(sql)
                    }
                }
            }

            callbacks.forEach {
                when(it) {
                    is DoorDatabaseCallbackSync -> it.onCreate(sqlDatabase)
                    is DoorDatabaseCallbackStatementList -> {
                        doorDb.execSQLBatch(*it.onCreate(sqlDatabase).toTypedArray())
                    }
                }
            }
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
                        .maxByOrNull { it.endVersion }
                if(nextMigration != null) {
                    when(nextMigration) {
                        is DoorMigrationSync -> nextMigration.migrateFn(sqlDatabase)
                        is DoorMigrationAsync -> runBlocking { nextMigration.migrateFn(sqlDatabase) }
                        is DoorMigrationStatementList -> doorDb.execSQLBatch(
                            *nextMigration.migrateStmts(sqlDatabase).toTypedArray())
                    }

                    currentDbVersion = nextMigration.endVersion
                    sqlDatabase.execSQL("UPDATE _doorwayinfo SET dbVersion = $currentDbVersion")
                }else {
                    throw IllegalStateException("Need to migrate to version " +
                            "${doorDb.dbVersion} from $currentDbVersion - could not find next migration")
                }
            }
        }

        callbacks.forEach {
            when(it) {
                is DoorDatabaseCallbackSync -> it.onOpen(sqlDatabase)
                is DoorDatabaseCallbackStatementList -> {
                    doorDb.execSQLBatch(*it.onOpen(sqlDatabase).toTypedArray())
                }
            }
        }

        connection.close()

        if(doorDb.dbType() == DoorDbType.POSTGRES) {
            val postgresChangeTracker = PostgresChangeTracker(doorDb as DoorDatabaseJdbc)
            postgresChangeTracker.setupTriggers()
        }

        return if(doorDb::class.doorDatabaseMetadata().hasReadOnlyWrapper) {
            doorDb.wrap(dbClass)
        }else {
            doorDb
        }
    }

    fun addCallback(callback: DoorDatabaseCallback) : DatabaseBuilder<T>{
        callbacks.add(callback)

        return this
    }

    fun addMigrations(vararg migrations: DoorMigration): DatabaseBuilder<T> {
        migrationList.addAll(migrations)
        return this
    }

    fun queryTimeout(seconds: Int){
        queryTimeout = seconds
    }

}