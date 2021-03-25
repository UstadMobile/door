package com.ustadmobile.door

/**
 * A request to listen for changes. This is used by LiveData and other items. The onChange
 * function will be run when a table is changed.
 *
 * @param tableNames A list (case sensitive) of the table names on which this listener should be invoked.
 * An empty list will result in the onChange method always being called
 *
 * @param onChange A function that will be executed when after a change has happened on a table.
 */
data class ChangeListenerRequest(val tableNames: List<String>, val onChange: (List<String>) -> Unit)
