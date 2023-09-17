package com.ustadmobile.door.test

import db2.ExampleDatabase2
import db3.ExampleDb3

expect suspend fun makeExample2Database(nodeId: Long): ExampleDatabase2

expect suspend fun makeExample3Database(nodeId: Long): ExampleDb3
