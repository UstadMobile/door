package com.ustadmobile.door.test

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext

/**
 * The default runTest kotlinx coroutines runTest uses an annoying virtual clock where delay simply advances the virtual
 * clock. In theory that could make some tests run faster, however it causes anything that runs
 *
 * withContext(Dispatchers.Default.limitedParallelism(1))
 *
 * Is the workaround as per the output from the Kotlinx
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun runTestWithRealClock(
    testBody: suspend TestScope.() -> Unit
): TestResult {
    return runTest {
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            testBody()
        }
    }
}