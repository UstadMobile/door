package com.ustadmobile.lib.annotationprocessor.core

import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.door.DoorDatabaseCallbackSync
import com.ustadmobile.door.DoorSqlDatabase
import db2.ExampleDatabase2
import org.junit.Test
import kotlin.test.assertEquals

class TestDatabaseBuilderCallback {

    class CreateEntityCallback: DoorDatabaseCallbackSync {
        override fun onCreate(db: DoorSqlDatabase) {
            db.execSQL("INSERT INTO ExampleEntity2(uid, name, someNumber, checked, rewardsCardNumber)" +
                    "VALUES (1, 'Bob', 5, 0, 42)")
        }

        override fun onOpen(db: DoorSqlDatabase) {
            //Do nothing
        }
    }

    @Test
    fun givenEntityInsertedByOnCreateCallback_whenDbOpened_thenShouldBeRetrievable() {
        val exampleDb2 = DatabaseBuilder.databaseBuilder( ExampleDatabase2::class, "jdbc:sqlite::memory:", 1L)
            .addCallback(CreateEntityCallback())
            .build()

        val createdOnCallback = exampleDb2.exampleDao2().findByUid(1)
        assertEquals(42, createdOnCallback?.rewardsCardNumber ?: -1,
            "Found entity that was created by insert using callback")
        exampleDb2.close()
    }

}