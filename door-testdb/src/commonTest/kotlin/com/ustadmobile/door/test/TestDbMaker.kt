package com.ustadmobile.door.test

import db2.ExampleDatabase2

expect fun makeInMemoryTestDb(nodeId: Long)

expect suspend fun makeExample2Database(nodeId: Long): ExampleDatabase2

