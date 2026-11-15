# Validation of Activa 0.1

Validated on 15 November 2026 with Eclipse Temurin JDK 17.0.17 and Maven 3.9.11 in the cloud workspace.

- `mvn clean verify`: **21 tests, 0 failures, 0 errors, 0 skipped**. JUnit covers registration/login, password hashing, duplicate accounts, CRUD, ownership, numeric/date/value errors, goal history and progress, unique-day streaks, backdated changes, point/badge recalculation, file restart, malformed/duplicate records, failed writes, encoded text fields, console error recovery, CLI arguments and idempotent demo seeding. Both domain interfaces are exercised.
- Four additional file-validation tests verify rejection of duplicate goal dates with differently cased UUIDs, malformed UTF-8 in encoded fields, and oversized emails; valid Unicode and maximum-length emails still load. Each rejection test checks that the original file remains byte-for-byte unchanged. The three rejection tests failed before the fixes and pass afterward.
- Packaged JAR: registered a new account, logged in, recorded walking and workout sessions, listed them, exited and restarted, edited walking to running, deleted the workout, set a daily goal, displayed completion/streak/rewards/statistics, and restarted again. The retained activity ID, 8000 steps, 40 minutes and 60 points were verified; deleted data stayed deleted.
- A second packaged JAR process targeting the same directory was rejected while the first held its lock.
- `java -jar target/activa-0.1.0.jar --help` and the documented demo arguments were executed. Invalid options/missing argument and corrupt-file startup return the expected nonzero statuses in the automated tests.
- Both PlantUML sources passed `-checkonly` and were rendered to the SVG files in this directory.
- Direct `javac --release 17` compilation of the application succeeded before Maven validation.
- Cloud setup was rerun using the retained JDK/Maven and dependency cache; `verify` and JAR help succeeded.
- `git diff --check` passed. Generated application data and build output are excluded from Git.

The cloud machine needs Maven's inherited HTTPS proxy and the platform CA in a Java trust store. A helper outside the repository, `/workspace/tools/activa-mvn`, applies these settings and keeps Maven's local cache under `/workspace/tools/m2`. Thus the cloud equivalents are:

```sh
cd /workspace/Programming-3-Project
/workspace/tools/activa-mvn clean verify
/workspace/tools/jdk-17.0.17+10/bin/java -jar target/activa-0.1.0.jar --demo
```

TLS and archive checksum verification remained enabled. This cloud configuration is separate from the portable Maven project and is unnecessary on a typical IntelliJ desktop installation.

IntelliJ GUI execution and the classroom presentation were not performed here. Restoration into a new cloud instance was verified: the retained checkout and toolchain built offline, the tests passed, and demo login/statistics/restart persistence worked. No additional publication was performed. The source project uses the standard Maven structure and Java 17 release target for IntelliJ import.
