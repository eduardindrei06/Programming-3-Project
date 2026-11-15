# Activa 0.1 — MID-TERM / WORK IN PROGRESS

**Activa is currently a work-in-progress university project. This version implements the Programming 3 mid-term requirements and is not the final application.**

Activa helps users record physical activity, follow daily goals, build streaks and earn virtual badges. This version uses a console and local files. It has no application framework, database or external service.

## Build and run

Install a **JDK 17 or later** (a JRE alone cannot compile) and Maven 3.9+. From the repository root:

```sh
mvn clean verify
java -jar target/activa-0.1.0.jar
java -jar target/activa-0.1.0.jar --demo
java -jar target/activa-0.1.0.jar --data-dir /path/to/private-data
java -jar target/activa-0.1.0.jar --help
```

Normal data defaults to `data/`. Demo data defaults to `demo-data/`. Relative paths resolve from the working directory. Quote paths containing spaces. `--demo --data-dir PATH` is also supported. Unknown arguments and missing path arguments are rejected with exit status 2; startup/storage errors return 1. Normal exit returns 0. Runtime application code uses only standard Java; Maven downloads JUnit and build plugins for development.

Without Maven, the standard source tree also works with `javac` (POSIX shell):

```sh
mkdir -p out
javac --release 17 -d out $(find src/main/java -name '*.java')
java -cp out activa.Main --demo
```

### IntelliJ IDEA

Open this repository's `pom.xml` as a Maven project, select JDK 17 as the project SDK and reload Maven. Run or debug `activa.Main` using the gutter icon. Set the run configuration working directory to the repository root and optionally set program arguments to `--demo`. Right-click `src/test/java` to run the JUnit tests, or use Maven's `verify` lifecycle. IntelliJ's bundled Maven is sufficient. No IDE-specific files are required or tracked. Tests and the packaged application were verified on the cloud command line; an IntelliJ GUI session was not tested.

## Implemented features and rules

- Register, login and logout. Usernames (3–30 ASCII letters, digits or underscores) and emails are unique ignoring case. Passwords contain 8–128 characters and are stored as salted **PBKDF2-HMAC-SHA256**, 210,000 iterations, 256-bit output, random 128-bit salt. A real terminal hides passwords; IntelliJ/redirected input displays them with a notice.
- Add, view, replace/edit and delete walking, running and workout activities. Each activity has a UUID, owner, date, minutes, steps and distance. Dates must be real and no later than today. Duration is 1–1440 minutes, steps 0–200000, distance a finite 0–1000 km. These per-record limits are simple plausibility checks; manually entered sessions may overlap.
- Set daily step/minute targets. Zero disables one target; at least one must be positive. Both enabled targets must be met. Progress shows actual totals and percentages (which may exceed 100%). Goal versions apply from the day they are set until replaced by a later version. Changing a goal on the same day replaces that day's version and recalculates its bonus; past days retain their earlier targets. Days before the first goal have no goal or bonus.
- An **active day** has at least one valid activity (at least one minute). Unique dates determine streaks, regardless of entry order or number of sessions. Current streak ends today, or yesterday if today has no activity yet; otherwise it is zero. Longest streak covers all recorded history. Deletions and date edits recalculate both. Dates use the machine's local calendar/time zone.
- **Points:** one point per recorded minute, plus 20 once per active date meeting its effective goal. Every successful edit, deletion, goal change and startup derives totals again from records. Badges are unique catalog entries at 30 (First Steps), 150 (Building Momentum), and 500 (Activa Champion) points. Falling below a threshold removes that badge; badges are not permanently earned. No increments or badge copies are persisted, so reloads cannot duplicate awards.
- All-time activity count, steps and minutes; Monday–Sunday daily summary for the current calendar week; streak, points and badges. Future days in that week show zero.
- Each logged-in user sees and modifies only their own activities/goals. UUIDs are shown in the list and copied into edit/delete prompts. Editing replaces all activity details while keeping the UUID.

## OOP and collections

| Class/interface | Responsibility |
| --- | --- |
| `User` | Identity, account details, password hash and derived total points |
| `Activity` / `ActivityType` | Immutable physical activity and supported types |
| `DailyGoal` | A dated version of a user's recurring daily targets |
| `Streak` | Calculates current and longest consecutive active days; identified by user ID |
| `Reward` | Immutable shared virtual badge catalog entry (Java record) |
| `PointAwardable` | `calculatePoints()`; implemented by Activity, used by the service |
| `GoalEvaluable` | `isComplete(steps, minutes)`; implemented by DailyGoal, used for bonuses |
| `ActivaService` | Login session, ownership checks, validation, goal history and derived totals |
| `AppData` / `FileStore` | In-memory collections and text snapshot parsing/writing |
| `PasswordHasher` | Salted password hashing and constant-time hash comparison |
| `ConsoleUi` / `Main` | Menu/input handling, arguments, data lock and demo startup |

`HashMap<UUID, User>` stores accounts by ID; `ArrayList<Activity>` and `ArrayList<DailyGoal>` support adding, filtering, replacing and deleting records. A `TreeSet<LocalDate>` removes duplicate activity dates and sorts them for streaks. A `HashMap<LocalDate, long[]>` aggregates daily totals. Collections are actively used, not placeholder declarations. Domain classes use private fields, constructors and getters. There is no artificial entity inheritance: the meaningful polymorphism is supplied by the two interfaces.

## File persistence

`FileStore` writes UTF-8 `activa.tsv`, with `ACTIVA<TAB>1` as a version header. `U` records contain UUID, Base64-encoded username, email and password hash; `A` records contain UUID, user UUID, type, ISO date, minutes, steps, km and record version `1`; `G` records contain UUID, user UUID, effective date and both targets. Base64 prevents tabs/newlines in string fields from breaking parsing; it is **encoding, not encryption**. Passwords themselves are never saved. Users, activities and goal history are persisted together; points, streaks and unlocked rewards are derived.

Missing files start empty. Loading creates fresh collections and rejects corrupt formats, duplicate IDs/accounts/goal dates, invalid values and orphaned owner IDs. Encoded fields must contain valid UTF-8; stored emails follow the same 254-character limit as registration. Goal ownership is compared using parsed UUIDs, so letter case cannot bypass duplicate-date checks. A malformed snapshot stops startup with an error and is left untouched. Correct or restore a backup before retrying; it is never silently reset.

Every accepted mutation writes a temporary file in the same directory and atomically replaces the snapshot before updating the live service state. Save failures report an error and reject that mutation. There is no exit-only save to forget. Atomic replacement must be supported by the chosen filesystem. A process lock prevents simultaneous Activa consoles from overwriting the same directory. This is simple local persistence, not a database or a backup system; keep private backups of important data. Files contain personal information and password hashes and are not encrypted. Do not commit them. Custom data directories outside the defaults must also be kept outside Git.

## Errors and validation

`IOException` reports failed reads/writes; a failed write keeps the previous accepted in-memory state. `NumberFormatException` reports invalid/overflowing numeric input. `DateTimeParseException` reports malformed or impossible dates. `IllegalArgumentException` handles invalid menu data, enums, UUIDs and goals. End of input exits cleanly. Invalid menu choices display the valid range.

Two checked custom exceptions are thrown and handled: `AccountException` for invalid/duplicate registration or rejected login, and `InvalidActivityException` for invalid activity values or inaccessible/nonexistent activity IDs. The UI catches them and returns to the menu. Tests demonstrate both. This is local educational authentication, not a production account server.

## Tests and university checklist

Run `mvn clean verify`. JUnit tests cover account validation/login/hash salts, two activity types and CRUD, cross-user ownership, invalid inputs, goal progress/history, interface implementations, streak gaps/duplicates/backdating, rewards/point recalculation, save failure, corrupt and duplicate records, escaping, restart persistence and console error handling. See [validation notes](docs/VALIDATION.md) for the checks actually run.

| Mid-term requirement | Implementation/evidence |
| --- | --- |
| Presentation (2 points) | Working console menus; [presentation guide](PRESENTATION_GUIDE.md); actual classroom presentation remains yours |
| OOP/UML (3 points) | Six domain classes, two implemented/used interfaces, collections; [class diagram](docs/class-diagram.puml) and [use case diagram](docs/use-cases.puml) |
| Files (1 point) | Atomic text snapshot, startup loading and restart tests |
| Arguments (optional, 1 point) | `--demo`, `--data-dir`, `--help` |
| Three standard exceptions (optional, 1 point) | I/O, numeric and date failures handled |
| Two custom exceptions (optional, 1 point) | AccountException and InvalidActivityException, exercised by tests |
| Input validation (optional, 1 point) | Account, date, numeric, goal, ID and ownership validation |

Rendered diagrams: [class diagram](docs/class-diagram.svg) and [use case diagram](docs/use-cases.svg).

PlantUML sources can be rendered in an IntelliJ PlantUML plugin or any local PlantUML installation; rendering is not needed to compile Activa. The class diagram shows domain fields and key operations, plus supporting class dependencies. The supplied `Eduard_Indrei.pdf` was read as the original concept; this implementation and its diagrams refine it for the requested mid-term scope.

## Demo and future development

`--demo` seeds four activities across three consecutive dates and today's goal only when there are no users. Login is **demo / ActivaDemo123**. Subsequent launches preserve changes and do not seed again. Use a fresh separate directory for a fresh demo. Sample credentials are public demonstration data, never real account credentials.

Postponed: GUI, Spring Boot, MySQL, REST APIs, automatic steps/GPS, mobile/watch integration, advanced statistics and rewards, personalization and screen-time controls. Screen-time settings/simulation are not implemented in 0.1. Data parsing and business rules are separated from the console to support later development without introducing those features now.

No commit or push is performed automatically. Review the diff, run the checks and commit the mid-term version before your university's deadline; the date was not supplied. Continue future work in this same repository.
