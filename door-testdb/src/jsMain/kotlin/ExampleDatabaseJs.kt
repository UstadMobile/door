import ExampleDatabaseJs.Companion.DB_VERSION
import androidx.room.Database
import com.ustadmobile.door.*
import com.ustadmobile.door.annotation.MinSyncVersion
import com.ustadmobile.door.entities.*
import db2.*
import wrappers.SQLiteDatasourceJs

@Database(version = DB_VERSION, entities = [ExampleJsEntity::class])
@MinSyncVersion(1)
abstract class ExampleDatabaseJs(open val datasource: SQLiteDatasourceJs): DoorDatabase(), SyncableDoorDatabase {
    abstract fun exampleJsDao(): ExampleDaoJs

    companion object {
        const val DB_VERSION = 2
    }
}