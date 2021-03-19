package wrappers

import org.khronos.webgl.Uint8Array

data class WorkerResult(var id:Int, var results: Array<Any>?, var ready: Boolean, var buffer: Uint8Array?)