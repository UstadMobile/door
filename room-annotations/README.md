# room-annotations

This module provides a copy of Room annotations so that they can be used on multiplatform code. 

It must be excluded from any Android app build to avoid a duplicate class error. Normally it would be used as a compileOnly 
dependency, however [Kotlin bug KT-43500](https://youtrack.jetbrains.com/issue/KT-43500) means that any consumer using 
the JS-IR compiler will not compile.

Using expect/actual on annotation won't work because 1) Room's symbol processor won't recognize the link and 2) using
a typealias prevents multiplatform code from using default parameters.

