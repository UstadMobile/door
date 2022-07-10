package androidx.room

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ustadmobile.door.RoomJdbcImpl
import com.ustadmobile.door.SupportSQLiteDatabaseConnectionImpl
import com.ustadmobile.door.ext.useStatement
import com.ustadmobile.door.jdbc.SQLException
import com.ustadmobile.door.jdbc.ext.mapRows
import com.ustadmobile.door.jdbc.ext.useResults
import com.ustadmobile.door.util.DoorCommonConstants.DBINFO_TABLENAME
import kotlinx.coroutines.Runnable
import java.io.File
import java.util.concurrent.Callable
import javax.naming.InitialContext
import javax.sql.DataSource
import kotlin.IllegalStateException
import kotlin.reflect.KClass

actual abstract class RoomDatabase actual constructor() {

    actual class Builder<T: RoomDatabase>(
        private val dbClass: KClass<T>,
        private val dbName: String,
        private val queryTimeoutSecs: Int = 10,
    ) {

        private val callbacks = mutableListOf<RoomDatabase.Callback>()

        private val migrationList = mutableListOf<Migration>()

        fun addCallback(callback: Callback) {
            callbacks += callback
        }

        fun addMigration(migration: Migration) {
            migrationList += migration
        }

        @Suppress("UNCHECKED_CAST")
        fun build(): T {
            val iContext = InitialContext()
            val dataSource = iContext.lookup("java:/comp/env/jdbc/${dbName}") as DataSource
            val dbImplClass = Class.forName("${dbClass.java.canonicalName}_JdbcKt") as Class<T>

            val doorDb = dbImplClass.getConstructor(RoomDatabase::class.java, DataSource::class.java,
                String::class.java, File::class.java, List::class.java, Int::class.javaPrimitiveType)
                .newInstance(null, dataSource, dbName, "", listOf<Any>(), queryTimeoutSecs)

            val connection = dataSource.connection
            val sqlDatabase = SupportSQLiteDatabaseConnectionImpl(connection)

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
                    it.onCreate(sqlDatabase)
                }
            }else {
                var currentDbVersion: Int = connection.createStatement().use { stmt ->
                    stmt.executeQuery("SELECT dbVersion FROM _doorwayinfo").mapRows { result ->
                        result.getInt(1)
                    }
                }.first()

                while(currentDbVersion < doorDb.dbVersion) {
                    val nextMigration = migrationList.filter { it.startVersion == currentDbVersion}
                        .maxByOrNull { it.endVersion }
                    if(nextMigration != null) {
                        nextMigration.migrate(sqlDatabase)
                        currentDbVersion = nextMigration.endVersion
                        sqlDatabase.execSQL("UPDATE _doorwayinfo SET dbVersion = $currentDbVersion")
                    }else {
                        throw IllegalStateException("Need to migrate to version " +
                                "${doorDb.dbVersion} from $currentDbVersion - could not find next migration")
                    }
                }

            }

            callbacks.forEach {
                it.onOpen(sqlDatabase)
            }

            connection.close()

            return doorDb
        }
    }

    abstract class Callback {
        /**
         * Called when the database is created for the first time. This is called after all the
         * tables are created.
         *
         * @param db The database.
         */
        open fun onCreate(db: SupportSQLiteDatabase) {}

        /**
         * Called when the database has been opened.
         *
         * @param db The database.
         */
        open fun onOpen(db: SupportSQLiteDatabase) {}

        /**
         * Called after the database was destructively migrated
         *
         * @param db The database.
         */
        open fun onDestructiveMigration(db: SupportSQLiteDatabase) {}
    }


    abstract fun createAllTables(): List<String>

    actual abstract fun clearAllTables()

    abstract val dbVersion: Int

    actual abstract val invalidationTracker: InvalidationTracker

    open fun runInTransaction(runnable: Runnable) {
        runnable.run()
    }

    open fun <V> runInTransaction(callable: Callable<V>): V {
        return callable.call()
    }

    fun query(query: String, args: Array<Any>) {
        throw IllegalStateException("This is NOT active on JVM/JDBC. It is only present to allow Android to compile")
    }


    /**
     * Execute a batch of SQL Statements in a transaction. This is generally much faster
     * than executing statements individually.
     */
    open fun execSQLBatch(vararg sqlStatements: String) {
        val doorRoomImpl = this as RoomJdbcImpl
        doorRoomImpl.jdbcImplHelper.useConnection { connection ->
            connection.setAutoCommit(false)
            connection.createStatement().useStatement { statement ->
                sqlStatements.forEach { sql ->
                    try {
                        statement.executeUpdate(sql)
                    }catch(eInner: SQLException) {
                        throw eInner
                    }
                }
            }
            connection.commit()
        }
    }

}