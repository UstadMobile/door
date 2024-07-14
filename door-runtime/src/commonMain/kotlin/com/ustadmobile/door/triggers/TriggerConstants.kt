package com.ustadmobile.door.triggers

/**
 * Note: sqlite_master was renamed to sqlite_schema in SQLite 3.33.0 . Many Android versions ship with a lower
 * version than that as per:
 *
 * https://developer.android.com/reference/android/database/sqlite/package-summary.html
 *
 */
object TriggerConstants {

    const val SQLITE_SELECT_TRIGGER_NAMES = """
        SELECT name
                  FROM sqlite_master
                 WHERE type = 'trigger'
                   AND name LIKE ?
    """

    const val SQLITE_SELECT_VIEW_NAMES = """
        SELECT name
          FROM sqlite_master
         WHERE type = 'view'
           AND name LIKE ?
    """

}