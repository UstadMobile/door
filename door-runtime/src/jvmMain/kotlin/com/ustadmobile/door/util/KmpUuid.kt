package com.ustadmobile.door.util

import java.util.*

actual fun randomUuid(): KmpUuid = UUID.randomUUID()

actual typealias KmpUuid = java.util.UUID
