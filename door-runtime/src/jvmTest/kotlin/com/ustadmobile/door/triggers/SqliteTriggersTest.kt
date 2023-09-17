package com.ustadmobile.door.triggers

import com.ustadmobile.door.annotation.Trigger
import kotlinx.coroutines.runBlocking
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals

class SqliteTriggersTest {

    @Test
    fun givenDatabaseCreated_whenDropDoorTriggersCalled_thenTriggersShouldBeRemoved() {
        val sqliteConnection = DriverManager.getConnection("jdbc:sqlite::memory:")
        sqliteConnection.createStatement().use {
            it.executeUpdate("""
                CREATE TABLE DiscussionPost(
                    postUid INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, 
                    title VARCHAR,
                    postTime INTEGER NOT NULL
                )    
            """)

            it.executeUpdate("CREATE VIEW DiscussionPost_ReceiveView AS SELECT * FROM DiscussionPost")

            //View that was not created by door as the receive view should be left alone
            it.executeUpdate("CREATE VIEW MyManualView AS SELECT COUNT(*) FROM DiscussionPost")

            it.executeUpdate("""
                CREATE TRIGGER ${Trigger.NAME_PREFIX}_demo AFTER INSERT ON DiscussionPost
                FOR EACH ROW
                BEGIN
                    UPDATE DiscussionPost SET name = 'first_name';
                END    
            """)

            //Trigger that does not have the door prefix shoudl be left alone
            it.executeUpdate("""
                CREATE TRIGGER my_own_trigger AFTER INSERT ON DiscussionPost
                FOR EACH ROW
                BEGIN
                    UPDATE DiscussionPost SET name = 'first_name';
                END    
            """)
        }

        runBlocking {
            val initialTriggerNames = sqliteConnection.getSqliteDoorTriggerNames()
            val initialViewNames = sqliteConnection.getSqliteDoorReceiveViewNames()

            sqliteConnection.dropDoorTriggersAndReceiveViews()

            assertEquals(1, initialTriggerNames.size)
            assertEquals(1, initialViewNames.size)

            assertEquals(0, sqliteConnection.getSqliteDoorTriggerNames().size)
            assertEquals(0, sqliteConnection.getSqliteDoorReceiveViewNames().size)
        }

    }

}