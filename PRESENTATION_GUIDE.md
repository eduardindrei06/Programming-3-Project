# Activa 0.1 presentation guide

## Introduction (about 30 seconds)

“Activa is my fitness activity tracking application. It helps people keep track of exercise and stay motivated through daily goals, streaks and virtual rewards. This is the Programming 3 mid-term version, not the finished application. For now, I use a console and local files.”

## Explain the structure

“User stores account information. Activity describes one walking, running or workout session. DailyGoal stores step and minute targets with an effective date. Streak calculates consecutive active dates. Reward describes a badge and its points threshold.

Activity implements PointAwardable, so it provides a way to calculate points. DailyGoal implements GoalEvaluable, so it can check whether daily totals meet the targets. The service uses both interfaces when calculating points and goal bonuses.

I use an ArrayList to store and filter activities, and a HashMap to look up users by UUID. A TreeSet removes repeated dates and puts them in order for streak calculation. ConsoleUi handles input, ActivaService handles rules, and FileStore handles files.”

Open the [class diagram](docs/class-diagram.puml) and [use case diagram](docs/use-cases.puml). Explain the one-user-to-many-activities and one-user-to-many-goal-versions relationships. Streaks and rewards are calculated views, not independent saved records. UUID foreign keys represent ownership, rather than Java object references from every entity.

## Prepare before class

1. Open `pom.xml` in IntelliJ, choose JDK 17, reload Maven and run tests.
2. Run Main with `--demo --data-dir demo-data/presentation`. Use a fresh directory for predictable sample data. Do not delete your real data.
3. Confirm that login `demo / ActivaDemo123` works. The demo has four activities over three days, 125 points, a three-day streak and one badge. Today's goal is 8000 steps and 30 minutes; the sample achieves both. The current day and time zone come from the computer.
4. Keep the terminal output large enough to read and the diagrams ready. Do not show real account data.

## Live walkthrough (about 5–8 minutes)

1. Register `student1`, `student1@example.com`, and a demonstration password of at least eight characters. Try registering the same username to show AccountException handled with a helpful message.
2. Log in. Add WALKING for today's date, 30 minutes, 6000 steps, 4 km. Add WORKOUT today, 20 minutes, 0 steps, 0 km.
3. View activities. Explain UUID and user ownership. Set a goal of 8000 steps and 30 minutes. Open daily progress: 75% steps and more than 100% minutes; both enabled targets are required.
4. Copy the walking UUID, edit it to 8000 steps with the same date and duration. Progress is complete. Points are 50 activity points plus one 20-point daily bonus. The First Steps badge is unlocked.
5. Show weekly statistics and streaks. Two activities today count as one active day. Add one activity for yesterday and one for the day before: the streak becomes three. Missing dates break it.
6. Delete the workout by UUID. Its minutes and points disappear. The daily bonus remains because walking alone still meets both goals. Explain that deleting/editing records recalculates totals instead of incrementing them again.
7. Try a non-number for duration, an impossible date such as `2026-02-30`, then a negative duration. Try deleting a random valid UUID. Explain NumberFormatException, DateTimeParseException, InvalidActivityException and how the menu recovers.
8. Logout, exit, restart with the exact same arguments and log in again. View activities and points. Explain that successful changes were saved immediately, and that restarting does not duplicate records, points or badges.
9. If time is short, use the seeded demo account to show its three-day streak, rewards and weekly summary.

## Explain persistence and limitations

“I save a versioned UTF-8 text snapshot. String fields are encoded so delimiters cannot break them. Passwords use salted PBKDF2 hashes, not plaintext. I save into a temporary file, then replace the old file atomically. I load fresh collections on startup and derive points and streaks from activities. If data is corrupt, startup reports it and preserves the file.

There is no database or real screen-time control yet. A GUI, Spring Boot, MySQL and device integrations are possible later. My current goal is a small working OOP application that I can explain and extend.”

## Git and submission

Review source, tests, diagrams and documentation in IntelliJ's Commit window. Exclude generated data and build files. Make sure the mid-term version is committed and pushed to the university repository before its deadline. A copy on GitHub alone is not a university submission. Commits and pushes by an assistant require your explicit authorization. A possible sequence is domain/business logic, persistence/console, then tests/documentation, provided each staged commit includes the files it needs.
