package com.ustadmobile.door.test

/**
 * This expect/actual class is a workaround that is required for a test on commonTest to pass when
 * running in androidUnitTest. Room requires Android context, which requires robolectric when running
 * in androidUnitTest as per:
 *
 * https://github.com/robolectric/robolectric/issues/7942
 *
 * The actual on androidUnitTest contains the required Robolectric runner annotation.
 */
expect abstract class AbstractCommonTest() {
}
