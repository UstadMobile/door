package com.ustadmobile.door.triggers

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ustadmobile.door.DoorConstants
import com.ustadmobile.door.annotation.Trigger
import com.ustadmobile.door.ext.doorDatabaseMetadata
import com.ustadmobile.door.triggers.TriggerConstants.SQLITE_SELECT_TRIGGER_NAMES
import kotlin.reflect.KClass

/**
 * RoomDatabase.Callback that will take care of Triggers as per the ReplicateEntity and Triggers annotation.
 *
 * This callback maintains a view called DoorTriggerVersionView which returns a single row representing the version of
 * the database for which triggers were last setup.
 *
 * OnCreate: If the database is newly created (onCreate is called), then all the triggers that are required will be created.
 *
 * OnOpen: If the database has been updated, this will be caught by using the aforementioned trigger. All previously
 * created triggers will be dropped and new ones will be created as per the DoorDatabaseMetadata.
 */
class DoorTriggerCallback(
    val currentDbVersion: Int,
    private val dbClass: KClass<out RoomDatabase>,
): RoomDatabase.Callback() {

    private val createTriggerVersionViewSql: String
        get() = """
            CREATE VIEW $TRIGGER_VERSION_VIEWNAME AS 
                   SELECT $currentDbVersion AS $VIEW_VERSION_FIELD_NAME 
        """

    override fun onCreate(db: SupportSQLiteDatabase) {
        val metadata = dbClass.doorDatabaseMetadata()
        metadata.createSqliteTriggerSetupStatementList().forEach { sql ->
            db.execSQL(sql)
        }

        db.execSQL(createTriggerVersionViewSql)
    }

    override fun onOpen(db: SupportSQLiteDatabase) {
        val triggerVersionInDb = db.query(
            "SELECT $VIEW_VERSION_FIELD_NAME FROM $TRIGGER_VERSION_VIEWNAME"
        ).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

        if(triggerVersionInDb != currentDbVersion) {
            val triggers = db.query(SQLITE_SELECT_TRIGGER_NAMES, arrayOf("${Trigger.NAME_PREFIX}%")).use { cursor ->
                buildList {
                    while(cursor.moveToNext()) {
                        add(cursor.getString(0))
                    }
                }
            }

            triggers.forEach { triggerName ->
                db.execSQL("DROP TRIGGER $triggerName")
            }

            val receiveViewNames = db.query(TriggerConstants.SQLITE_SELECT_VIEW_NAMES, arrayOf("%${DoorConstants.RECEIVE_VIEW_SUFFIX}")).use { cursor ->
                buildList {
                    while(cursor.moveToNext()) {
                        add(cursor.getString(0))
                    }
                }
            }
            receiveViewNames.forEach { receiveViewName ->
                db.execSQL("DROP VIEW $receiveViewName")
            }

            dbClass.doorDatabaseMetadata().createSqliteTriggerSetupStatementList().forEach { sql ->
                db.execSQL(sql)
            }

            db.execSQL("DROP VIEW $TRIGGER_VERSION_VIEWNAME")
            db.execSQL(createTriggerVersionViewSql)
        }
    }

    companion object {

        const val TRIGGER_VERSION_VIEWNAME = "DoorTriggerVersionView"

        const val VIEW_VERSION_FIELD_NAME = "triggerDbVersion"

    }

}