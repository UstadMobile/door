package com.ustadmobile.door

import com.ustadmobile.door.attachments.AttachmentStorage
import com.ustadmobile.door.room.RoomDatabase
import com.ustadmobile.door.util.DoorJsImplClasses
import kotlin.reflect.KClass

sealed class DatabaseBuilderOptions<T : RoomDatabase>(
    var dbClass: KClass<T>,
    var dbImplClasses: DoorJsImplClasses<T>,
    var dbUrl: String = "indexeddb:${dbClass.simpleName!!}",
    var jdbcQueryTimeout: Int = 10,
    var attachmentStorage: AttachmentStorage?,
)
