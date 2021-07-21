package com.ustadmobile.door.ext

actual  fun String.urlEncode(): String = java.net.URLEncoder.encode(this, "UTF-8")
