package com.ustadmobile.door.log

import io.github.aakira.napier.LogLevel

/**
 * Napier's standard DebugAntilog() cannot be inherited from. This expect/actual is basically a copy of the DebugAntilog()
 * for each platform, only the is log enabled function is set to check for the minimum log level
 */
expect class NapierAntilog(minLogLevel: LogLevel): AbstractNapierAntilog {

}