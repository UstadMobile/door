# Door

[![Release](https://jitpack.io/v/UstadMobile/door.svg)](https://jitpack.io/#UstadMobile/door)
![JS](https://img.shields.io/badge/platform-android-orange)
![JVM](https://img.shields.io/badge/platform-jvm-orange)
![JS](https://img.shields.io/badge/platform-js-orange)

Door is a Kotlin Symbol Processor that builds on [Room](https://developer.android.com/training/data-storage/room) to
generate a complete full-stack offline-first database system, including HTTP REST server endpoints, local data sources 
and client repositories. Door supports both pull and push operations.

Platforms supported:
* **Android**: Door will generate the actual class for Android, which in turn is then used by Room to generate the
  implementation (the same implementation if you had used Room itself). Full support for auto-generation of offline-first
  repository.
* **JVM**: Door supports SQLite and PostgreSQL using JDBC. Door will generate the entire implementation for you to run
  queries using JDBC and return results. Full support for auto-generation of offline-first repository.
* **Javascript**: Door uses SQLite.js to run SQLite within the web browser. Full support for auto-generation of 
  offline-first repository.

No support for iOS/Native (yet - pull request would be welcome. Happy to help support anyone who would like to work on
this).

Return types supported:
 * Blocking queries (except on JS)
 * Async queries (e.g. using suspend)
 * Flow results
 * PagingSource using [multiplatform-paging](https://github.com/cashapp/multiplatform-paging) - ready to go for compose
   multiplatform.


Example Entity:
```
@Entity
@Triggers()
class Widget() {
    @PrimaryKey(autoIncrement = true)
    var widgetUid: Long = 0
    
    var widgetPrice: Float = 0f
    
    var widgetName: String? = null
    
    @ReplicationEtag
    @ReplicationLastModified
    var widgetLastModified: Long = 0
}
```

Example DAO:

```
expect class MyDao {

    @HttpAccessible
    suspend fun findAllWidgets(): List<Widget>
    
}
```

**REST Server**:

Door will automatically generate a KTOR REST endpoint. You can add it to your KTOR server using doorRoute.
```
val myDb = DatabaseBuilder.builder(DbClass::class, "jdbc:sqlite:file.db")
               .build()
route("mydatasource") {
    doorRoute(myDb)
}
```

Now an HTTP get to /mydatasource/MyDao/findAllWidgets will return the list.

**Android Client**:
```
val myDb = DatabaseBuilder.builder(DbClass::class, "MyDb", context)
          .build()

val repository = myDb.asRepository("http://mserver.com/mydatasource/")

val widgets = repository.myDao.findAllWidgets()
```

## Getting started

Very basic example repo: [https://github.com/UstadMobile/door-example](https://github.com/UstadMobile/door-example)

1. Add Gradle dependencies:
```
//Add jitpack repository if you don't already have it
buildscript {
    repositories {
        mavenCentral()
        google()
        
        maven {
           url 'https://jitpack.io/'
        }
    }
}

plugins {
    id "org.jetbrains.kotlin.multiplatform"
    id "com.android.library"
    
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
                implementation "com.github.UstadMobile.door:room-annotations:$version_door"
            }
        }
        
        androidMain {
            dependencies {
                //Add Room dependencies for Android
                implementation "androidx.room:room-runtime:$version_android_room"
                implementation "androidx.room:room-ktx:$version_android_room"
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

On your Android application module (e.g. the one building the APK), you **must** exclude Door's room-annotations to avoid a
duplicate class error. This is required due to a Kotlin/JS bug as per this [README](room-annotations/).

```
configurations.all {
    exclude group: "com.github.UstadMobile.door", module: "room-annotations"
}
```

2. Now create your database, DAOs, and entities in Kotlin multiplatform common code:

Database:
```
import com.ustadmobile.door.room.RoomDatabase
import com.ustadmobile.door.annotations.DoorDatabase

@DoorDatabase(version = 1, 
  entities = [ MyEntity::class, AnotherEntity::class])
expect abstract class MyDatabase: RoomDatabase {
     
     val myEntityDao: MyEntityDao
     
     val anotherEntityDao: AnotherEntityDao 
}
```

DAO:
```
import com.ustadmobile.door.annotations.DoorDao

@DoorDao
expect abstract class MyEntityDao {
     
     @Query("SELECT * FROM MyEntity WHERE id = :id")
     fun myQuery(id: Int): MyEntity?
     
}
```

Entity:
```
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
class MyEntity() {
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0
    
    var name: String? = null
    
    var rewardsCardNumber: Int = 0
}
```

3. Create and use the database in your code. Database creation is platform-specific, so it's best to use multiplatform dependency injection
(such as KodeIN-DI) or create your own expect-actual function.

JVM:
```
//Option 1: Use an SQLite JDBC URL 
val sqliteDatabase = DatabaseBuilder.databaseBuilder(MyDatabase::class, "jdbc:sqlite:path/to/file.sqlite").build()

//Option 2: Use a Postgres JDBC URL
val postgresDatabase = DatabaseBuilder.databaseBuilder(MyDatabase::class, "jdbc:postgres:///mydbname", 
  dbUsername = "pguser", dbPassword = "secret").build()

//Option 3: Use a JNDI DataSource (e.g. using within an application server etc)
val jndiDatabase = DatabaseBuilder.databaseBuilder(MyDatabase::class, "java:/comp/env/jdbc/myDB")
```
Android:
```
val myDatabase = DatabaseBuilder.databaseBuilder(context, MyDatabase::class, "mydatabase").build()
```

Javascript 
```
//Note: build() is a suspended function on Javascript
//MyDatabase2JsImplementations is a generated class that needs to be given as an argument
//sqliteJsWorkerUrl should be a URL to the SQLite.js worker - see https://sql.js.org/#/?id=downloadingusing
val builderOptions = DatabaseBuilderOptions(
  MyDatabase::class, MyDatabase2JsImplementations, "sqlite:my_indexdb_name",sqliteJsWorkerUrl)
val myDatabase = DatabaseBuilder.databaseBuilder(builderOptions).build() 
```

Limitations:
* Because we are using expect/actual, no function body can be added (better to use extension functions).
* No support for choosing entity constructors. Door requires and will always choose the no args constructor.
* No support for Room @Relation annotation or Multimap return types
* No support for TypeConverter

## Debugging
Use Gradle in debug mode e.g.:
```
./gradlew --no-daemon -Dorg.gradle.debug=true build
```

## Automatic REST endpoint generation

Door eliminates the need to manually create boilerplate REST endpoints. If entities are marked with @ReplicateEntity, then Door 
automatically uses the @Etag field as an ETag over http. Just annotate the query as HttpAccessible

```
@HttpAccessible
susped fun findEntityByPrimaryKey(primaryKey: Long): MyEntity?
```

When you use the automatically generated repository, Door will automatically make a http request. If there is changed
Data, door will insert it (the same as if the data was received via replication). Results that return a list will use a
hashed etag based on all etags in the list returned.


## Postgres/SQLite query differentiation

Most of the time SQL that works on SQLite works on Postgres, and vice-versa. But not always. Door provides a few 
workarounds.

Option 1: Define a different query for postgres (both queries must have the same named parameters).
```
@Query("""
       REPLACE INTO TableName(col1, col2) 
        SELECT 1 as col1, 2 as col2
         WHERE NOT EXISTS (...)
       """)
@PostgresQuery("""
              INSERT INTO TableName1(col1, col2)
              SELECT 1 as col1, 2 as col2
              ON CONFLICT(col1) DO UPDATE
              SET col2 = EXCLUDED.col2
              """)     
```

Option 2: Use comment hacks:
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
* [room-annotations](room-annotations/) Contains androidx annotations identical to those used in room,
used to compile on non-Android targets.
* [door-compiler](door-compiler/) Contains the actual annotation processor based on Kotlin Poet
* [door-runtime](door-runtime/) The main runtime module - contains classes and functions used by generated
code
* [door-testdb](door-testdb/) Contains a few test databases that are used for unit and integration testing. These 
databases are compiled by the annotation processor, so tests can verify functionality.




## Known issues

* door-testdb:jsBrowserTest on a limited Internet connection may fail. The test has to download SQLite.js
from the Internet due to issues with asset loading. It can be skipped if building locally.

* When updating dependencies: Wrong kotlin lock upgrade message: use kotlinUpgradeYarnLock not kotlinUpgradePackageLock as per  https://youtrack.jetbrains.com/issue/KT-66714

