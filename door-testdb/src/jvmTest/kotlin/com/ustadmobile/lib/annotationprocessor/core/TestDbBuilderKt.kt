package com.ustadmobile.lib.annotationprocessor.core

import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.door.DoorDatabase
import db2.ExampleDatabase2
import db2.ExampleEntity2
import db2.ExampleLinkEntity
import org.junit.Assert
import org.junit.Before
import org.junit.BeforeClass

import org.junit.Test
import java.io.File
import java.lang.reflect.Method
import java.net.URL
import java.net.URLClassLoader

class TestDbBuilderKt {

    lateinit var exampleDb2: ExampleDatabase2

    @Before
    fun openAndClearDb() {
        exampleDb2 = DatabaseBuilder.databaseBuilder(Any(), ExampleDatabase2::class, "db1").build()
        exampleDb2.clearAllTables()
    }

    //@Test
    fun givenDbShouldOpen() {
        /*
        To make this run in Android Studio:
          1. Copy the jndi config (resources) to ./build/classes/test/lib-database-annotation-processor_jvmTest/
          2. Run the Gradle compile task compileTestKotlinJvm
          3. Use reflection to load classes that were created by the annotation processor
        val addMethod = URLClassLoader::class.java.getDeclaredMethod("addURL", URL::class.java)
        addMethod.isAccessible = true
        addMethod.invoke(ClassLoader.getSystemClassLoader(),
                File("/home/mike/src/UstadMobile/lib-database-annotation-processor/build/classes/kotlin/jvm/test").toURI().toURL())
        */
        Assert.assertNotNull(exampleDb2)
        val exampleDao2 = exampleDb2.exampleDao2()
        Assert.assertNotNull(exampleDao2)
        val exList = listOf(ExampleEntity2(0, "bob",42))
        exampleDao2.insertList(exList)
        Assert.assertTrue(true)
    }


    @Test
    fun givenEntryInserted_whenQueried_shouldBeEqual() {
        val entityToInsert = ExampleEntity2(0, "Bob", 50)
        entityToInsert.uid = exampleDb2.exampleDao2().insertAndReturnId(entityToInsert)

        val entityFromQuery = exampleDb2.exampleDao2().findByUid(entityToInsert.uid)

        Assert.assertNotEquals(0, entityToInsert.uid)
        Assert.assertEquals("Entity retrieved from database is the same as entity inserted",
                entityToInsert, entityFromQuery)
    }


    @Test
    fun givenEntryInserted_whenSingleValueQueried_shouldBeEqual() {
        val entityToInsert = ExampleEntity2(0, "Bob" + System.currentTimeMillis(),
                50)
        entityToInsert.uid = exampleDb2.exampleDao2().insertAndReturnId(entityToInsert)


        Assert.assertEquals("Select single column method returns expected value",
                entityToInsert.name, exampleDb2.exampleDao2().findNameByUid(entityToInsert.uid))
    }

    @Test
    fun givenEntitiesInserted_whenQueryWithEmbeddedValueRuns_shouldReturnBoth() {
        val entityToInsert = ExampleEntity2(0, "Linked", 50)
        entityToInsert.uid = exampleDb2.exampleDao2().insertAndReturnId(entityToInsert)

        val linkedEntity = ExampleLinkEntity((Math.random() * 1000).toLong(), entityToInsert.uid)
        exampleDb2.exampleLinkedEntityDao().insert(linkedEntity)

        val entityWithEmbedded = exampleDb2.exampleDao2().findByUidWithLinkEntity(entityToInsert.uid)
        Assert.assertEquals("Embedded entity is loaded into query result",
                linkedEntity.eeUid, entityWithEmbedded!!.link!!.eeUid)
    }

    @Test
    fun givenEntityInsertedWithNoCorrespondingEmbeddedEntity_whenQueryWithEmbeddedValueRuns_embeddedObjectShouldBeNull() {
        val entityToInsert = ExampleEntity2(0, "Not Linked", 50)
        entityToInsert.uid = exampleDb2.exampleDao2().insertAndReturnId(entityToInsert)

        val entityWithEmbedded = exampleDb2.exampleDao2().findByUidWithLinkEntity(entityToInsert.uid)

        Assert.assertNull("Embedded entity is null where there is no matching entity on the right hand side of join",
                entityWithEmbedded!!.link)
    }


    @Test
    fun givenEntitiesInserted_whenFindAllCalled_shouldReturnBoth(){
        val entities = listOf(ExampleEntity2(name = "e1", someNumber = 42),
                ExampleEntity2(name = "e2", someNumber = 43))
        exampleDb2.exampleDao2().insertList(entities)

        val entitiesFromQuery = exampleDb2.exampleDao2().findAll()
        Assert.assertEquals("Found correct number of entities inserted",2,
                entitiesFromQuery.size)
    }

    @Test
    fun givenEntityInserted_whenUpdateSingleItemNoReturnTypeCalled_thenValueShouldBeUpdated() {
        val entityToInsert = ExampleEntity2(name = "UpdateMe", someNumber =  50)
        entityToInsert.uid = exampleDb2.exampleDao2().insertAndReturnId(entityToInsert)

        val nameBeforeInsert = exampleDb2.exampleDao2().findNameByUid(entityToInsert.uid)

        entityToInsert.name = "Update${System.currentTimeMillis()}"
        exampleDb2.exampleDao2().updateSingleItem(entityToInsert)

        Assert.assertEquals("Name before insert is first given name", "UpdateMe",
                nameBeforeInsert)
        Assert.assertEquals("Name after insert is updated name", entityToInsert.name,
                exampleDb2.exampleDao2().findNameByUid(entityToInsert.uid))
    }

    @Test
    fun givenEntityInserted_whenUpdatedSingleItemReturnCountCalled_thenShouldUpdateAndReturn1() {
        val entityToInsert = ExampleEntity2(name = "UpdateMe", someNumber =  50)
        entityToInsert.uid = exampleDb2.exampleDao2().insertAndReturnId(entityToInsert)

        entityToInsert.name = "Update${System.currentTimeMillis()}"

        val updateCount = exampleDb2.exampleDao2().updateSingleItemAndReturnCount(entityToInsert)

        Assert.assertEquals("Update count = 1", 1, updateCount)
        Assert.assertEquals("Name after insert is updated name", entityToInsert.name,
                exampleDb2.exampleDao2().findNameByUid(entityToInsert.uid))
    }

    @Test
    fun givenEntitiesInserted_whenUpdateListNoReturnTypeCalled_thenBothItemsShouldBeUpdated() {
        val entities = listOf(ExampleEntity2(name = "e1", someNumber = 42),
                ExampleEntity2(name = "e2", someNumber = 43))
        entities.forEach { it.uid = exampleDb2.exampleDao2().insertAndReturnId(it) }

        entities.forEach { it.name += System.currentTimeMillis() }
        exampleDb2.exampleDao2().updateList(entities)

        entities.forEach { Assert.assertEquals("Entity was updated", it.name,
                exampleDb2.exampleDao2().findNameByUid(it.uid)) }
    }


}