package com.ustadmobile.door

import kotlin.reflect.KClass

data class DatabaseBuilderOptions(
    var dbClass: KClass<*>,
    var dbImplClass: KClass<*>,
    var dbName: String = dbClass.simpleName!!,

    /**
     * Path to SQL.JS web worker
     */
    var webWorkerPath: String,

    /**
     * Delay time before saving the database to indexed database
     */
    val saveToIndexedDbDelayTime: Long = 5000
)
