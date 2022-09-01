
## Door

Door Kotlin Symbol Processor that builds on [Room](https://developer.android.com/training/data-storage/room) and makes
it possible to use Room databases, DAOs and entities with Kotlin Multiplatform. Just 
just put your Database and DAOs in your Kotlin Multiplatform common code, and door will generate expect/actuals for you!
Door supports:
* **Android**: Door will generate the actual class for Android, which in turn is then used by Room to generate the 
implementation (the same implementation if you had used Room itself).
* **JVM**: Door supports SQLite and PostgreSQL using JDBC. Door will generate the entire implementation for you to run 
queries using JDBC and return results.
* **Javascript** Door supports SQLite in the browser through SQLite.JS. Door will generate the entire implementation for
you to run the queries and return results. Only asynchronous operations are supported (suspended functions, LiveData, 
and DataSource.Factory)

No support for iOS/Native (yet - pull request would be welcome. Happy to help support anyone who would like to work on 
this)


## Getting started
Just create your Database and DAOs in Kotlin multiplatform common code:

```
import com.ustadmobile.door.room.RoomDatabase
import com.ustadmobile.door.annotations.DoorDatabase

@DoorDatabase(version = 1, 
  entities = [ MyEntity::class, AnotherEntity::class])
expect abstract class MyDatabase: RoomDatabase {
     
     val myEntityDao: MyEntityDao
     
     val anotherEntityDao: AnotherEntityDao 
}

import com.ustadmobile.door.annotations.DoorDao

@DoorDao
expect abstract class MyEntityDao {
     
     @Query("SELECT * FROM MyEntity WHERE id = :id")
     fun myQuery(id: Int): MyEntity?
     
}
```

Then add Gradle dependencies:
```
plugins {
    id "org.jetbrains.kotlin.multiplatform"
    //Add Kotlin Symbol Processing Plugin
    id "com.google.devtools.ksp"
}

kotlin {
    android { }

    jvm { }
    
    js { }
    
    sourceSets {
        commonMain {
            dependencies {
                //Add Door itself
                implementation "com.github.UstadMobile.door:door-runtime:$version_door"
                compileOnly "com.github.UstadMobile.door:door-annotations:$version_door"
            }
        }
        
        androidMain {
            dependencies {
                //Add Room dependencies for Android
                implementation "androidx.room:room-runtime:$version_android_room"
                implementation "androidx.room:room-ktx:$version_android_room"
                implementation "androidx.lifecycle:lifecycle-livedata-ktx:$version_androidx_lifecycle"
                implementation "androidx.paging:paging-runtime:$version_androidx_paging"
            }
        }
    }
}

dependencies {
    //Add the Door and Room Kotlin Symbol Processors for applicable platforms
    kspJvm "com.github.UstadMobile.door:door-compiler:$version_door"
    kspJs "com.github.UstadMobile.door:door-compiler:$version_door"
    kspAndroid "com.github.UstadMobile.door:door-compiler:$version_door"
    kspAndroid "androidx.room:room-compiler:$version_android_room"
}
```

Then create the database. Database creation is platform-specific, so it's best to use a dependency injection library 
(such as KodeIN-DI).

```
JVM: 
//Make sure a JDBC DataSource is bound to: 
// java:/comp/env/jdbc/mydatabase
// This will be updated to allow the direct use of a JDBC URL
val myDatabase = DatabaseBuilder.databaseBuilder(MyDatabase::class, "mydatabase").build()

Android:
val myDatabase = DatabaseBuilder.databaseBuilder(context, MyDatabase::class, "mydatabase").build()

Javascript 
//Note: build() is a suspended function on Javascript
val builderOptions = DatabaseBuilderOptions(
  MyDatabase::class, MyDatabase2JsImplementations, "my_indexdb_name",workerBlobUrl)
val myDatabase = DatabaseBuilder.databaseBuilder(builderOptions).build() 

```

Limitations:
* Because we are using expect/actual, no function body can be added (better to use extension functions).


## Debugging
Use Gradle in debug mode e.g.:
```
./gradlew --no-daemon -Dorg.gradle.debug=true build
```

## Postgres/SQLite query hacks

Most of the time SQL that works on SQLite works on Postgres, and vice-versa. But not always.

Door provides a few workarounds:

```
@Query("""
  -- Replace into will be turned into INSERT INTO 
  REPLACE INTO TableName(col1, col2)
          SELECT 1 as col1, 2 as col2
  --notpsql
  -- Anything here will NOT run on Postgres
  WHERE NOT EXISTS(...)
  --endnotpsql
  
   /*psql 
   -- Anything here will ONLY run on Postgres
   ON CONFLICT(col1) DO UPDATE
   SET col2 = EXCLUDED.col2  
   */
""")
```


## Android permissions:

* android.permission.ACCESS_NETWORK_STATE - Used to automatically turn replication on and off when a device is 
  connected and disconnected.

## Modules

* [app-testdb](app-testdb/) Contains a test React/JS app. Used to run manual tests that don't seem to work in automated
JS tests.
* [door-jdbc](door-jdbc/) Contains multiplatform expect-actuals representing the core of JDBC (e.g. required functions on Statement, 
PreparedStatement, Connection, DataSource, etc) On Android and JVM these are just typealiases to the real JDBC interfaces. 
On JS these are actual interfaces. The class names are the same as found in JDBC, only the package name has been changed 
from java.sql.* to com.ustadmobile.door.jdbc*. This allows multiplatform code to use JDBC.
* [door-room-jdbc](door-room-jdbc/) Contains an Android module that provides an implementation of door-jdbc for Room 
databases. 
* [door-compiler](door-compiler/) Contains the actual annotation processor based on Kotlin Poet
* [door-room-kmp](door-room-kmp/) Provides the needed androidx classes (e.g. LiveData, annotations, etc) for 
use on JVM and JS. This is a compileOnly dependency for common code and an implementation dependency on JVM and JS. 
Android will use room itself as an implementation dependency.
* [door-testdb](door-testdb/) Contains a few test databases that are used for unit and integration testing. These 
databases are compiled by the annotation processor, so tests can verify functionality (e.g. insert then retrieve, 
sync, etc)

## Known issues

* door-testdb:jsBrowserTest on a limited Internet connection may fail. The test has to download SQLite.js
from the Internet due to issues with asset loading. It can be skipped if building locally.
