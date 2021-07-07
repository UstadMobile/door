package com.ustadmobile.door.util

import kotlin.js.Date


actual fun systemTimeInMillis(): Long = Date().getTime().toLong()