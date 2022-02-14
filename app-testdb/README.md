## JS Test app

door-testdb:jsTest was not respecting waiting in tests, so this module
contains a manual test. 

Usage:
1. Build and run door-testdb-server
```
./gradlew door-testdb-server:shadowJar
cd door-testdb-server
./door-testdb-server.sh
```
2. Run the test
```
./gradlew app-testdb:browserDevelopmentRun --continuous
```
3. Look at the browser console.

