package com.ustadmobile.door

import kotlin.reflect.KClass

data class DatabaseBuilderOptions(var dbClass: KClass<*>,
                                  var dbImplClass: KClass<*>,
                                  var dbName: String = dbClass.simpleName!!)
