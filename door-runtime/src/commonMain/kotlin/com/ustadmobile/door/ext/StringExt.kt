package com.ustadmobile.door.ext

fun String.hexStringToByteArray() = this.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

