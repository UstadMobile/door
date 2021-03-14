package com.ustadmobile.door.ext

/**
 * As per:
 *
 * https://discuss.kotlinlang.org/t/is-there-a-way-to-use-the-new-operator-with-arguments-on-a-dynamic-variable-in-kotlin-javascript/6126/3
 *
 */
fun <T : Any> JsClass<T>.createInstance(vararg args: dynamic): dynamic {
    @Suppress("UNUSED_VARIABLE")
    val ctor = this
    @Suppress("UNUSED_VARIABLE")
    val argsArray = (listOf(null) + args).toTypedArray()

    //language=JavaScript 1.6
    return js("new (Function.prototype.bind.apply(ctor, argsArray))")
}

