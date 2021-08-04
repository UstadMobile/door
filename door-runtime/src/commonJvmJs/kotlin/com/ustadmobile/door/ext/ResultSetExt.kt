package com.ustadmobile.door.ext
import com.ustadmobile.door.jdbc.ResultSet

/**
 * Used by generated code to make a map of column name to the index of the column. This is used by generated code.
 */
fun ResultSet.columnIndexMap() : Map<String, Int> {
    return getMetaData().let { metaData ->
        (1 .. metaData.getColumnCount()).map { metaData.getColumnLabel(it) to it }.toMap()
    }
}