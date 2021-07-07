package com.ustadmobile.door.ext

import kotlinext.js.Object
import kotlinext.js.asJsObject

/**
 * A function that is designed ot generate an instance identifier. This is System.identityHashCode
 * on JVM. TBD on Javascript - could be something like
 * https://stackoverflow.com/questions/194846/is-there-any-kind-of-hash-code-function-in-javascript
 */

actual val Any.doorIdentityHashCode: Int
    get() = generateHashCode(this)

private fun generateHashCode(value: Any): Int{
    val type = js("value == undefined ? undefined : typeof value")
    return when {
        type != "undefined" && type != "object" -> generateHashCodeFromNonObject(value.toString())
        type == "object" -> generateHashCodeFromObject(value.asJsObject())
        else -> 0
    }
}

private fun generateHashCodeFromNonObject(value: String):Int {
    var generatedHashCode = 0
    for(i in value.indices){
        val charCode = js("value.charCodeAt(i)").toString().toInt()
        generatedHashCode = (((generatedHashCode shl 7) - generatedHashCode) + charCode) and 0xFFFFFFFF.toByte().toInt()
    }
    return generatedHashCode
}

private fun generateHashCodeFromObject(value: Object): Int{
    var generatedHashCode = 0
    Object.keys(value).forEach { key ->
        if(value.hasOwnProperty(key)){
            val hashCode = generateHashCode(value.asDynamic()[key])
            generatedHashCode += generateHashCodeFromNonObject("${generatedHashCode + hashCode}")
        }
    }
    return generatedHashCode
}
