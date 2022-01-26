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
