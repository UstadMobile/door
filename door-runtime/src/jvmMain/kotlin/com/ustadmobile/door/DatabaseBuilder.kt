package com.ustadmobile.door

import com.ustadmobile.door.room.RoomDatabase
import com.ustadmobile.door.DoorConstants.DBINFO_TABLENAME
import com.ustadmobile.door.attachments.AttachmentFilter
import com.ustadmobile.door.attachments.FileAttachmentStorage
import com.ustadmobile.door.ext.doorDatabaseMetadata
import com.ustadmobile.door.ext.wrapDoorDatabase
import com.ustadmobile.door.migration.DoorMigration
import com.ustadmobile.door.migration.DoorMigrationAsync
import com.ustadmobile.door.migration.DoorMigrationStatementList
import com.ustadmobile.door.migration.DoorMigrationSync
import kotlinx.coroutines.runBlocking
import java.io.File
import java.lang.IllegalStateException
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import javax.naming.InitialContext
import javax.sql.DataSource
import kotlin.reflect.KClass
import com.ustadmobile.door.jdbc.ext.useResults
import com.ustadmobile.door.jdbc.ext.mapRows
import com.ustadmobile.door.room.InvalidationTracker
import com.ustadmobile.door.room.PostgresInvalidationTracker
import com.ustadmobile.door.room.SqliteInvalidationTracker
import com.ustadmobile.door.util.InvalidationTrackerDbBuiltListener
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.sqlite.SQLiteConfig
import org.sqlite.SQLiteDataSource


@Suppress("unused") //This is used as an API
class DatabaseBuilder<T: RoomDatabase> internal constructor(
    private var dbClass: KClass<T>,
    private var dbUrl: String,
    private var dbUsername: String?,
    private var dbPassword: String?,
    private var attachmentDir: File? = null,
    private var attachmentFilters: List<AttachmentFilter> = mutableListOf(),
    private var queryTimeout: Int = PreparedStatementConfig.STATEMENT_DEFAULT_TIMEOUT_SECS,
){

    private val callbacks = mutableListOf<DoorDatabaseCallback>()

    private val migrationList = mutableListOf<DoorMigration>()

    companion object {
        fun <T : RoomDatabase> databaseBuilder(
            dbClass: KClass<T>,
            dbUrl: String,
            dbUsername: String? = null,
            dbPassword: String? = null,
            attachmentDir: File? = null,
            attachmentFilters: List<AttachmentFilter> = listOf(),
            queryTimeout: Int = PreparedStatementConfig.STATEMENT_DEFAULT_TIMEOUT_SECS,
        ): DatabaseBuilder<T> = DatabaseBuilder(dbClass, dbUrl, dbUsername, dbPassword, attachmentDir,
            attachmentFilters, queryTimeout)
    }

    @Suppress("UNCHECKED_CAST")
    fun build(): T {
        val dataSource = when {
            dbUrl.startsWith("jdbc:") -> {
                val jdbcUrlType = dbUrl.substringAfter("jdbc:").substringBefore(":")
                if(jdbcUrlType != "postgresql" && jdbcUrlType != "sqlite") {
                    throw IllegalArgumentException("Invalid database type: $jdbcUrlType " +
                            "- only postgres and sqlite are supported")
                }

                if(jdbcUrlType == "sqlite") {
                    HikariDataSource(HikariConfig().apply {
                        /*
                         SQLite in-memory mode will wipe everything whenever there is a new connection. This means
                         we must work with a SINGLE connection.
                         */
                        if(dbUrl.endsWith(":memory:")) {
                            maximumPoolSize = 1
                            minimumIdle = 1
                            maxLifetime = Long.MAX_VALUE
                        }

                        isAutoCommit = true
                        dataSource = SQLiteDataSource(SQLiteConfig().apply {
                            setJournalMode(SQLiteConfig.JournalMode.WAL)
                            setBusyTimeout(30000)
                            setSynchronous(SQLiteConfig.SynchronousMode.OFF)
                            enableRecursiveTriggers(true)
                        }).apply {
                            url = dbUrl
                        }
                    })
                }else {
                    HikariDataSource().apply {
                        isAutoCommit = true
                        jdbcUrl = dbUrl
                        dbUsername?.also { username = it }
                        dbPassword?.also { password = it }
                    }
                }
            }
            dbUrl.startsWith("java:/") -> {
                //do JNDI lookup
                val iContext = InitialContext()
                iContext.lookup(dbUrl) as DataSource
            }
            else -> {
                throw IllegalArgumentException("Invalid database url: $dbUrl : " +
                        "must be either a valid jdbc Postgres or SQLite URL, or JNDI path ")
            }
        }

        dataSource.connection.use { connection ->
            val dbType = DoorDbType.typeIntFromProductName(connection.metaData?.databaseProductName ?: "")

            val dbImplClass = Class.forName("${dbClass.java.canonicalName}_JdbcKt") as Class<T>
            val tableNamesArr = dbClass.doorDatabaseMetadata().allTables.toTypedArray()
            val invalidationTracker = if(dbType == DoorDbType.SQLITE) {
                SqliteInvalidationTracker(tableNamesArr, setupTriggersBeforeConnection = true)
            }else {
                PostgresInvalidationTracker(dataSource, *tableNamesArr)
            }

            val doorDb = dbImplClass.getConstructor(RoomDatabase::class.java, DataSource::class.java,
                String::class.java, File::class.java, List::class.java, Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType, InvalidationTracker::class.java, Boolean::class.javaPrimitiveType)
                .newInstance(null, dataSource, dbUrl, attachmentDir, attachmentFilters, queryTimeout,
                    dbType, invalidationTracker, dbUrl.startsWith("jdbc:"))

            val sqlDatabase = DoorSqlDatabaseConnectionImpl(connection)

            val tableNames: List<String> = connection.metaData.getTables(null, null, "%", arrayOf("TABLE")).useResults { tableResult ->
                tableResult.mapRows { it.getString("TABLE_NAME") ?: "" }
            }

            if(!tableNames.any {it.lowercase() == DBINFO_TABLENAME}) {
                //Do this directly to avoid conflicts with the change tracking systems before tables have been created
                connection.createStatement().use { stmt ->
                    doorDb.createAllTables().forEach { sql ->
                        stmt.executeUpdate(sql)
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
                var stmt = null as Statement?
                var resultSet = null as ResultSet?

                var currentDbVersion = -1
                try {
                    stmt = connection.createStatement()
                    resultSet = stmt.executeQuery("SELECT dbVersion FROM _doorwayinfo")
                    if(resultSet.next())
                        currentDbVersion = resultSet.getInt(1)
                }catch(e: SQLException) {
                    throw e
                }finally {
                    resultSet?.close()
                    stmt?.close()
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

            runBlocking { (invalidationTracker as? InvalidationTrackerDbBuiltListener)?.onDatabaseBuilt(connection) }

            callbacks.forEach {
                when(it) {
                    is DoorDatabaseCallbackSync -> it.onOpen(sqlDatabase)
                    is DoorDatabaseCallbackStatementList -> {
                        doorDb.execSQLBatch(*it.onOpen(sqlDatabase).toTypedArray())
                    }
                }
            }

            return if(doorDb::class.doorDatabaseMetadata().hasReadOnlyWrapper) {
                doorDb.wrapDoorDatabase(dbClass, attachmentDir?.let { FileAttachmentStorage(it) })
            }else {
                doorDb
            }
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