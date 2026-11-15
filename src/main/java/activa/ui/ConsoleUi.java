package activa.ui;
import activa.exception.*;
import activa.model.*;
import activa.service.ActivaService;
import java.io.*;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

public final class ConsoleUi {
    private final ActivaService service;
    private final Scanner input;
    public ConsoleUi(ActivaService service, InputStream input) { this.service = service; this.input = new Scanner(input); }
    public void run() {
        System.out.println("Activa 0.1 — mid-term / work in progress");
        boolean loggedIn = false;
        while (true) {
            try {
                if (!loggedIn) {
                    System.out.println("\n1. Register  2. Login  0. Exit");
                    switch (read("Choice")) {
                        case "0" -> { return; }
                        case "1" -> register();
                        case "2" -> {
                            String name = read("Username"); char[] password = password();
                            try { service.login(name, password); loggedIn = true; System.out.println("Welcome, " + service.currentUser().getUsername()); }
                            finally { Arrays.fill(password, '\0'); }
                        }
                        default -> System.out.println("Choose 0, 1 or 2.");
                    }
                } else {
                    System.out.println("\n1. Add activity  2. View activities  3. Edit activity  4. Delete activity\n"
                            + "5. Set daily goal  6. Daily progress  7. Streaks  8. Points and rewards  9. Weekly statistics  0. Logout");
                    switch (read("Choice")) {
                        case "0" -> { service.logout(); loggedIn = false; }
                        case "1" -> activity(null);
                        case "2" -> {
                            if (service.activities().isEmpty()) System.out.println("No activities yet.");
                            service.activities().forEach(System.out::println);
                        }
                        case "3" -> activity(UUID.fromString(read("Activity ID (from View activities)")));
                        case "4" -> { service.deleteActivity(UUID.fromString(read("Activity ID"))); System.out.println("Deleted and saved."); }
                        case "5" -> {
                            int steps = integer("Target steps (0 disables)"), minutes = integer("Target minutes (0 disables)");
                            service.setGoal(steps, minutes); System.out.println("Goal saved, effective today.");
                        }
                        case "6" -> progress();
                        case "7" -> {
                            Streak s = service.streak(); System.out.println("Current streak: " + s.getCurrentStreak() + "; longest: " + s.getLongestStreak());
                        }
                        case "8" -> {
                            System.out.println("Points: " + service.currentUser().getTotalPoints());
                            System.out.println("Unlocked rewards: " + service.rewards());
                        }
                        case "9" -> statistics();
                        default -> System.out.println("Choose a menu option from 0 to 9.");
                    }
                }
            } catch (NoSuchElementException e) { System.out.println("\nInput ended. All successful changes are saved."); return; }
            catch (NumberFormatException e) { System.out.println("Enter a valid number within the supported range."); }
            catch (DateTimeParseException e) { System.out.println("Enter a real date in YYYY-MM-DD format."); }
            catch (InvalidActivityException | AccountException e) { System.out.println(e.getMessage()); }
            catch (IllegalArgumentException e) { System.out.println("Invalid input: " + e.getMessage()); }
            catch (IOException e) { System.out.println("Could not save: " + e.getMessage() + ". Change was not accepted. Check storage and retry."); }
        }
    }
    private String read(String prompt) { System.out.print(prompt + ": "); return input.nextLine().trim(); }
    private int integer(String prompt) { return Integer.parseInt(read(prompt)); }
    private char[] password() {
        if (System.console() != null) {
            char[] result = System.console().readPassword("Password: ");
            if (result == null) throw new NoSuchElementException();
            return result;
        }
        System.out.print("Password (visible in IDE/redirected console): "); return input.nextLine().toCharArray();
    }
    private void register() throws AccountException, IOException {
        String name = read("Username"), email = read("Email"); char[] password = password();
        try { service.register(name, email, password); System.out.println("Account saved. You can now log in."); }
        finally { Arrays.fill(password, '\0'); }
    }
    private void activity(UUID id) throws InvalidActivityException, IOException {
        ActivityType type = ActivityType.valueOf(read("Type (WALKING, RUNNING, WORKOUT)").toUpperCase(Locale.ROOT));
        LocalDate date = LocalDate.parse(read("Date (YYYY-MM-DD)"));
        int minutes = integer("Duration in minutes"), steps = integer("Steps");
        double km = Double.parseDouble(read("Distance in km (decimal point)"));
        Activity a = service.saveActivity(id, type, date, minutes, steps, km);
        System.out.println("Activity saved: " + a.getId());
    }
    private void progress() {
        LocalDate day = LocalDate.parse(read("Date (YYYY-MM-DD)"));
        long steps = service.stepsOn(day), minutes = service.minutesOn(day);
        System.out.println(day + ": " + steps + " steps, " + minutes + " active minutes");
        service.goalOn(day).ifPresentOrElse(g -> {
            if (g.getTargetSteps() > 0) System.out.printf(Locale.ROOT, "Steps: %d/%d (%.1f%%)%n", steps, g.getTargetSteps(), steps * 100.0 / g.getTargetSteps());
            if (g.getTargetMinutes() > 0) System.out.printf(Locale.ROOT, "Minutes: %d/%d (%.1f%%)%n", minutes, g.getTargetMinutes(), minutes * 100.0 / g.getTargetMinutes());
            System.out.println(g.isComplete(steps, minutes) ? "Goal complete!" : "Goal in progress.");
        }, () -> System.out.println("No goal was set for that date."));
    }
    private void statistics() {
        List<Activity> activities = service.activities();
        System.out.println("All time: " + activities.size() + " activities, " + activities.stream().mapToLong(Activity::getSteps).sum()
                + " steps, " + activities.stream().mapToLong(Activity::getDurationMinutes).sum() + " minutes");
        LocalDate monday = service.today().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        System.out.println("Calendar week starting " + monday + ":");
        for (int i = 0; i < 7; i++) {
            LocalDate day = monday.plusDays(i);
            System.out.println(day + ": " + service.stepsOn(day) + " steps, " + service.minutesOn(day) + " minutes");
        }
        System.out.println("Current streak: " + service.streak().getCurrentStreak() + ", points: " + service.currentUser().getTotalPoints());
        System.out.println("Unlocked rewards: " + service.rewards());
    }
}
