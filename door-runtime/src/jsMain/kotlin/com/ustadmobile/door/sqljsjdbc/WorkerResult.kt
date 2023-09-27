package com.ustadmobile.door.sqljsjdbc

import js.typedarrays.Uint8Array

data class WorkerResult(var id:Int, var results: Array<Any>?, var ready: Boolean, var buffer: Uint8Array?)