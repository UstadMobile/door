package com.ustadmobile.door.jdbc.ext

import com.ustadmobile.door.jdbc.PreparedStatement
import com.ustadmobile.door.jdbc.TypesKmp

/**
 * Wrapper to handle setting a nullable primitive value correctly
 */
inline fun <T> PreparedStatement.setNullableParam(
    index: Int,
    value: T?,
    sqlType: Int,
    setter: (Int, T) -> Unit
) {
    if(value != null)
        setter(index, value)
    else
        setNull(index, sqlType)
}

@Suppress("unused") //Used by generated code
fun PreparedStatement.setIntNullable(index: Int, value: Int?) = setNullableParam(index, value, TypesKmp.INTEGER,
    this::setInt)

@Suppress("unused") //Used by generated code
fun PreparedStatement.setByteNullable(index: Int, value: Byte?) = setNullableParam(index, value, TypesKmp.SMALLINT,
    this::setByte)

@Suppress("unused") //Used by generated code
fun PreparedStatement.setShortNullable(index: Int, value: Short?) = setNullableParam(index, value, TypesKmp.SMALLINT,
    this::setShort)

@Suppress("unused") //Used by generated code
fun PreparedStatement.setLongNullable(index: Int, value: Long?) = setNullableParam(index, value, TypesKmp.BIGINT,
    this::setLong)

@Suppress("unused") //Used by generated code
fun PreparedStatement.setFloatNullable(index: Int, value: Float?) = setNullableParam(index, value, TypesKmp.FLOAT,
    this::setFloat)

@Suppress("unused") //Used by generated code
fun PreparedStatement.setDoubleNullable(index: Int, value: Double?) = setNullableParam(index, value, TypesKmp.DOUBLE,
    this::setDouble)

@Suppress("unused") //Used by generated code
fun PreparedStatement.setBooleanNullable(index: Int, value: Boolean?) = setNullableParam(index, value, TypesKmp.BOOLEAN,
    this::setBoolean)
