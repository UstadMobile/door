package com.ustadmobile.door.test

import com.ustadmobile.door.log.NapierAntilog
import io.github.aakira.napier.LogLevel
import io.github.aakira.napier.Napier
import kotlin.concurrent.Volatile

@Volatile
private var napierLogInitRan = false
fun initNapierLog(){
    //Napier.takeLogarithm does not seem to work - if Napier.base is called n times (e.g. by multiple tests) then
    // each log gets repeated n-1 times
    if(!napierLogInitRan) {
        Napier.base(NapierAntilog(LogLevel.DEBUG))
        napierLogInitRan = true
    }
}
