## Debugging the annotation processor itself

Run a task that runs the annotation processor (e.g. door-testdb:jvmJar) and add arguments:
--no-daemon -Dorg.gradle.debug=true . Then attach the debugger (In IntelliJ: Run, edit configurations,
Add Remote JVM Debug with port 5005)

KSP:
```
./gradlew :module:taskName --no-daemon -Dorg.gradle.debug=true -Pkotlin.compiler.execution.strategy=in-process
```

e.g.
```
./gradlew :door-testdb:jvmJar --no-daemon -Dorg.gradle.debug=true -Pkotlin.compiler.execution.strategy=in-process
```

