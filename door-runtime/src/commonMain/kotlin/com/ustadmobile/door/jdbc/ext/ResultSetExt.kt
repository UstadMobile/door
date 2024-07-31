package com.ustadmobile.door.jdbc.ext

import com.ustadmobile.door.jdbc.ResultSet

fun <R> ResultSet.useResults(block: (ResultSet) -> R) : R{
    try {
        return block(this)
    }finally {
        close()
    }
}

fun <R> ResultSet.mapRows(block: (ResultSet) -> R): List<R> {
    val mappedResults = mutableLinkedListOf<R>()
    while(next()) {
        mappedResults += block(this)
    }

    return mappedResults
}

@Suppress("unused") //Used by generated code
fun <R> ResultSet.mapNextRow(defaultVal: R, block: (ResultSet) -> R): R {
    return if(next())
        block(this)
    else
        defaultVal
}

@Suppress("unused") //Used by generated code
fun <R> ResultSet.mapNextRow(block: (ResultSet) -> R): R {
    return if(next())
        block(this)
    else
        throw NullPointerException("mapNextRow: no row and no default value provided")
}

inline fun <R> ResultSet.getOrNull(block: ResultSet.() -> R?): R?  {
    return block(this).let { if(wasNull()) null else it }
}


@Suppress("unused") //Used by generated code
fun ResultSet.getIntNullable(columnIndex: Int) = getOrNull { getInt(columnIndex) }

@Suppress("unused") //Used by generated code
fun ResultSet.getIntNullable(columnName: String) = getOrNull { getInt(columnName) }

@Suppress("unused") //Used by generated code
fun ResultSet.getByteNullable(columnIndex: Int) = getOrNull { getByte(columnIndex) }

@Suppress("unused") //Used by generated code
fun ResultSet.getByteNullable(columnName: String) = getOrNull { getByte(columnName) }

@Suppress("unused") //Used by generated code
fun ResultSet.getShortNullable(columnIndex: Int) = getOrNull {  getShort(columnIndex) }

@Suppress("unused") //Used by generated code
fun ResultSet.getShortNullable(columnName: String) = getOrNull { getShort(columnName) }

@Suppress("unused") //Used by generated code
fun ResultSet.getLongNullable(columnIndex: Int) = getOrNull { getLong(columnIndex) }

@Suppress("unused") //Used by generated code
fun ResultSet.getLongNullable(columnName: String) = getOrNull { getLong(columnName) }

@Suppress("unused") //Used by generated code
fun ResultSet.getFloatNullable(columnIndex: Int) = getOrNull { getFloat(columnIndex) }

@Suppress("unused") //Used by generated code
fun ResultSet.getFloatNullable(columnName: String) = getOrNull { getFloat(columnName) }

@Suppress("unused") //Used by generated code
fun ResultSet.getDoubleNullable(columnIndex: Int) = getOrNull { getDouble(columnIndex) }

@Suppress("unused") //Used by generated code
fun ResultSet.getDoubleNullable(columnName: String) = getOrNull { getDouble(columnName) }

@Suppress("unused") //Used by generated code
fun ResultSet.getBooleanNullable(columnIndex: Int) = getOrNull { getBoolean(columnIndex) }

@Suppress("unused") //Used by generated code
fun ResultSet.getBooleanNullable(columnName: String) = getOrNull { getBoolean(columnName) }

@Suppress("unused") //Used by generated code
fun ResultSet.getStringNonNull(columnName: String): String {
    return getString(columnName) ?: throw IllegalStateException("Column $columnName was supposed to be non-null, got null")
}

@Suppress("unused") //Used by generated code
fun ResultSet.getStringNonNull(columnIndex: Int): String {
    return getString(columnIndex) ?: throw IllegalStateException("Column $columnIndex was supposed to be non-null, got null")
}

