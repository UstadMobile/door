package com.ustadmobile.door

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Utility expect/actual function so we can easily get a dispatcher to run observe calls on
 */
actual fun doorMainDispatcher(): CoroutineDispatcher = Dispatchers.Main
