package com.ustadmobile.door

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Utility expect/actual function so we can easily get a dispatcher to run observe calls on
 */
expect fun doorMainDispatcher(): CoroutineDispatcher