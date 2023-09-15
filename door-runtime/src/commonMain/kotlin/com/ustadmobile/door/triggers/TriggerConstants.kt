package com.ustadmobile.door.triggers

object TriggerConstants {

    const val SQLITE_SELECT_TRIGGER_NAMES = """
        SELECT name
                  FROM sqlite_master
                 WHERE type = 'trigger'
                   AND name LIKE ?
    """

    const val SQLITE_SELECT_VIEW_NAMES = """
        SELECT name
                  FROM sqlite_schema
                 WHERE type = 'view'
                   AND name LIKE ?
    """

}