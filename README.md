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
