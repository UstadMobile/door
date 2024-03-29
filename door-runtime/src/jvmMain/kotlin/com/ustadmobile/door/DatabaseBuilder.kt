package com.ustadmobile.door

import com.ustadmobile.door.DoorConstants.DBINFO_TABLENAME
import com.ustadmobile.door.ext.doorDatabaseMetadata
import com.ustadmobile.door.jdbc.ext.mapRows
import com.ustadmobile.door.jdbc.ext.useResults
import com.ustadmobile.door.log.DoorLogger
import com.ustadmobile.door.log.NapierDoorLogger
import com.ustadmobile.door.log.i
import com.ustadmobile.door.log.v
import com.ustadmobile.door.message.DefaultDoorMessageCallback
import com.ustadmobile.door.message.DoorMessageCallback
import com.ustadmobile.door.migration.DoorMigration
import com.ustadmobile.door.migration.DoorMigrationAsync
import com.ustadmobile.door.migration.DoorMigrationStatementList
import com.ustadmobile.door.migration.DoorMigrationSync
import com.ustadmobile.door.room.RoomDatabase
import com.ustadmobile.door.room.RoomJdbcImpl
import com.ustadmobile.door.triggers.createTriggerSetupStatementList
import com.ustadmobile.door.triggers.dropDoorTriggersAndReceiveViews
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.runBlocking
import org.sqlite.SQLiteConfig
import org.sqlite.SQLiteDataSource
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import javax.naming.InitialContext
import javax.sql.DataSource
import kotlin.reflect.KClass


@Suppress("unused") //This is used as an API
class DatabaseBuilder<T: RoomDatabase> internal constructor(
    private var dbClass: KClass<T>,
    private var dbUrl: String,
    private var nodeId: Long,
    private var dbUsername: String?,
    private var dbPassword: String?,
    private var queryTimeout: Int = PreparedStatementConfig.STATEMENT_DEFAULT_TIMEOUT_SECS,
    private var messageCallback: DoorMessageCallback<T> = DefaultDoorMessageCallback(),
    private var logger: DoorLogger = NapierDoorLogger(),
    private var dbName: String = dbUrl,
){

    private val callbacks = mutableListOf<DoorDatabaseCallback>()

    private val migrationList = mutableListOf<DoorMigration>()

    companion object {
        fun <T : RoomDatabase> databaseBuilder(
            dbClass: KClass<T>,
            dbUrl: String,
            nodeId: Long,
            dbUsername: String? = null,
            dbPassword: String? = null,
            queryTimeout: Int = PreparedStatementConfig.STATEMENT_DEFAULT_TIMEOUT_SECS,
        ): DatabaseBuilder<T> = DatabaseBuilder(dbClass, dbUrl, nodeId, dbUsername, dbPassword, queryTimeout)
    }

    @Suppress("UNCHECKED_CAST")
    fun build(): T {
        val logPrefix = "[DatabaseBuilder.build - $dbName]"
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

                        dataSource = SQLiteDataSource(SQLiteConfig().apply {
                            setJournalMode(SQLiteConfig.JournalMode.WAL)
                            setBusyTimeout(30000)
                            setSynchronous(SQLiteConfig.SynchronousMode.OFF)
                            enableRecursiveTriggers(true)
                            isAutoCommit = true
                        }).apply {
                            url = dbUrl
                        }
                    })
                }else {
                    HikariDataSource().apply {
                        jdbcUrl = dbUrl
                        dbUsername?.also { username = it }
                        dbPassword?.also { password = it }
                        isAutoCommit = true
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

            val dbImplClass = Class.forName("${dbClass.java.canonicalName}_JdbcImpl") as Class<T>

            val doorDb = dbImplClass.getConstructor(RoomDatabase::class.java, DataSource::class.java,
                    String::class.java, String::class.java, Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType, DoorLogger::class.java)
                .newInstance(null, dataSource, dbUrl, dbName, queryTimeout, dbType, logger)


            val sqlDatabase = DoorSqlDatabaseConnectionImpl(connection, dbType)

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

                /**
                 * Setup initial trigger and receive views as per ReplicateEntity annotation
                 */
                connection.createStatement().use { stmt ->
                    dbClass.doorDatabaseMetadata().createTriggerSetupStatementList(dbType).forEach { triggerSetupSql ->
                        stmt.addBatch(triggerSetupSql)
                    }
                    stmt.executeBatch()
                    logger.v { "$logPrefix : created tables" }
                }

                callbacks.forEach {
                    when(it) {
                        is DoorDatabaseCallbackSync -> it.onCreate(sqlDatabase)
                        is DoorDatabaseCallbackStatementList -> {
                            sqlDatabase.execSQLBatch(it.onCreate(sqlDatabase).toTypedArray())
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

                val dbWillBeMigrated = currentDbVersion < doorDb.dbVersion
                /*
                 * If the database will be migrated, then all door-created triggers/views should be dropped. Will be
                 * recreated after migration
                 */
                if(dbWillBeMigrated) {
                    runBlocking { sqlDatabase.connection.dropDoorTriggersAndReceiveViews() }
                }

                while(currentDbVersion < doorDb.dbVersion) {
                    val nextMigration = migrationList.filter { it.startVersion == currentDbVersion}
                        .maxByOrNull { it.endVersion }
                    if(nextMigration != null) {
                        logger.v { "$logPrefix start update from ${nextMigration.startVersion} to ${nextMigration.endVersion}" }
                        when(nextMigration) {
                            is DoorMigrationSync -> nextMigration.migrateFn(sqlDatabase)
                            is DoorMigrationAsync -> runBlocking { nextMigration.migrateFn(sqlDatabase) }
                            is DoorMigrationStatementList -> {
                                connection.createStatement().use { migrateStmt ->
                                    nextMigration.migrateStmts(sqlDatabase).forEach {
                                        migrateStmt.addBatch(it)
                                    }
                                    migrateStmt.executeBatch()
                                }
                            }
                        }

                        currentDbVersion = nextMigration.endVersion
                        sqlDatabase.execSQL("UPDATE _doorwayinfo SET dbVersion = $currentDbVersion")
                        logger.v { "$logPrefix finish update from ${nextMigration.startVersion} to ${nextMigration.endVersion}" }
                    }else {
                        throw IllegalStateException("Need to migrate to version " +
                                "${doorDb.dbVersion} from $currentDbVersion - could not find next migration")
                    }
                }

                connection.takeIf { dbWillBeMigrated }?.createStatement()?.use { statement ->
                    dbClass.doorDatabaseMetadata().createTriggerSetupStatementList(dbType).forEach { sql ->
                        statement.addBatch(sql)
                    }
                    statement.executeBatch()
                }
            }

            callbacks.forEach {
                when(it) {
                    is DoorDatabaseCallbackSync -> it.onOpen(sqlDatabase)
                    is DoorDatabaseCallbackStatementList -> {
                        sqlDatabase.execSQLBatch(it.onOpen(sqlDatabase).toTypedArray())
                    }
                }
            }

            //Start change tracking
            (doorDb as RoomJdbcImpl).jdbcImplHelper.onStartChangeTracking()

            val wrapperClass = Class.forName("${dbClass.java.canonicalName}${DoorDatabaseWrapper.SUFFIX}") as Class<T>
            val dbWrapped = wrapperClass.getConstructor(dbClass.java, Long::class.javaPrimitiveType,
                    DoorMessageCallback::class.java, DoorLogger::class.java, String::class.java)
                .newInstance(doorDb, nodeId, messageCallback, logger, dbName)
            logger.i("$logPrefix database build complete")
            return dbWrapped
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

    fun messageCallback(messageCallback: DoorMessageCallback<T>): DatabaseBuilder<T> {
        this.messageCallback = messageCallback
        return this
    }

    fun logger(logger: DoorLogger) : DatabaseBuilder<T> {
        this.logger = logger
        return this
    }

    /**
     * Set the name that will be used for logging purposes
     */
    fun name(name: String) : DatabaseBuilder<T>{
        this.dbName = name
        return this
    }

    fun queryTimeout(seconds: Int){
        queryTimeout = seconds
    }

}