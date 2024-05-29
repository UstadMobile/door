package com.ustadmobile.door.replication

interface JsonDbFieldInfo {

    val fieldName: String

    /**
     * The SQL field type used in the database eg. INTEGER, BIGINT, etc as per TypesKmp
     */
    val dbFieldType: Int

    val nullable: Boolean

}