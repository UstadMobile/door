package com.ustadmobile.lib.annotationprocessor.core

import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.door.nodeevent.AbstractNodeEventTest
import db3.ExampleDb3

class NodeEventTest : AbstractNodeEventTest() {

    override fun makeExampleDb(nodeId: Long): ExampleDb3 {
        return DatabaseBuilder.databaseBuilder(ExampleDb3::class, "jdbc:sqlite::memory:", nodeId)
            .build().also {
                it.clearAllTables()
            }
    }

}