package activa;
import activa.exception.*;
import activa.interfaces.*;
import activa.model.*;
import activa.persistence.FileStore;
import activa.service.*;
import activa.ui.ConsoleUi;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ActivaTest {
    @TempDir Path directory;
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 15);
    private ActivaService service;
    private ActivaService open(LocalDate date) throws IOException {
        return new ActivaService(new FileStore(directory), Clock.fixed(date.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC));
    }
    @BeforeEach void setup() throws Exception {
        service = open(TODAY);
        service.register("alice", "alice@example.com", "password123".toCharArray());
        service.login("alice", "password123".toCharArray());
    }
    private Activity add(LocalDate date, int minutes, int steps) throws Exception {
        return service.saveActivity(null, ActivityType.WALKING, date, minutes, steps, 2);
    }
    @Test void registrationLoginAndHashing() throws Exception {
        String hash = service.currentUser().getPasswordHash();
        assertFalse(hash.contains("password123"));
        assertTrue(PasswordHasher.matches("password123".toCharArray(), hash));
        assertNotEquals(hash, PasswordHasher.hash("password123".toCharArray()));
        service.logout();
        assertThrows(IllegalStateException.class, service::activities);
        assertThrows(AccountException.class, () -> service.login("alice", "wrong".toCharArray()));
        service.login("ALICE", "password123".toCharArray());
        assertEquals("alice", service.currentUser().getUsername());
    }
    @Test void duplicateAndInvalidAccounts() {
        assertThrows(AccountException.class, () -> service.register("ALICE", "other@example.com", "password123".toCharArray()));
        assertThrows(AccountException.class, () -> service.register("bobby", "ALICE@example.com", "password123".toCharArray()));
        assertThrows(AccountException.class, () -> service.register("", "bad", new char[0]));
        assertThrows(AccountException.class, () -> service.register("bobby", "bad", "password123".toCharArray()));
        assertThrows(AccountException.class, () -> service.register("bobby", "bob@example.com", "short".toCharArray()));
    }
    @Test void crudAndInterfaces() throws Exception {
        Activity a = add(TODAY, 30, 6000);
        Activity workout = service.saveActivity(null, ActivityType.WORKOUT, TODAY, 20, 0, 0);
        assertEquals(2, service.activities().size());
        PointAwardable award = a; assertEquals(30, award.calculatePoints());
        GoalEvaluable goal = new DailyGoal(UUID.randomUUID(), a.getUserId(), TODAY, 6000, 30);
        assertTrue(goal.isComplete(6000, 30)); assertFalse(goal.isComplete(5999, 30));
        service.saveActivity(a.getId(), ActivityType.RUNNING, TODAY, 40, 7000, 5);
        assertEquals(60, service.currentUser().getTotalPoints());
        service.deleteActivity(workout.getId());
        assertEquals(1, service.activities().size()); assertEquals(40, service.currentUser().getTotalPoints());
    }
    @Test void preventsCrossUserEditsAndReads() throws Exception {
        Activity alice = add(TODAY, 30, 1000);
        service.register("bobby", "bob@example.com", "password456".toCharArray());
        service.login("bobby", "password456".toCharArray());
        assertTrue(service.activities().isEmpty());
        assertThrows(InvalidActivityException.class, () -> service.deleteActivity(alice.getId()));
        assertThrows(InvalidActivityException.class, () -> service.saveActivity(alice.getId(), ActivityType.WALKING, TODAY, 10, 0, 0));
        add(TODAY, 10, 500);
        assertEquals(10, service.currentUser().getTotalPoints());
        service.login("alice", "password123".toCharArray()); assertEquals(30, service.currentUser().getTotalPoints());
    }
    @Test void rejectsInvalidActivityValues() {
        for (int minutes : new int[]{-1, 0, 1441}) assertThrows(InvalidActivityException.class, () -> add(TODAY, minutes, 0));
        assertThrows(InvalidActivityException.class, () -> add(TODAY, 10, -1));
        assertThrows(InvalidActivityException.class, () -> add(TODAY.plusDays(1), 10, 0));
        for (double km : new double[]{-1, Double.NaN, Double.POSITIVE_INFINITY, 1001})
            assertThrows(InvalidActivityException.class, () -> service.saveActivity(null, ActivityType.WALKING, TODAY, 10, 0, km));
        assertThrows(InvalidActivityException.class, () -> service.deleteActivity(UUID.randomUUID()));
    }
    @Test void pointsAndRewardsAreDerivedWithoutDuplicates() throws Exception {
        service.setGoal(8000, 30);
        Activity a = add(TODAY, 30, 8000); add(TODAY, 5, 0);
        assertEquals(55, service.currentUser().getTotalPoints()); assertEquals(1, service.rewards().size());
        service.saveActivity(a.getId(), ActivityType.WALKING, TODAY, 30, 8000, 2);
        assertEquals(55, service.currentUser().getTotalPoints());
        for (int i = 0; i < 2; i++) {
            service = open(TODAY); service.login("alice", "password123".toCharArray());
            assertEquals(55, service.currentUser().getTotalPoints()); assertEquals(1, service.rewards().size());
        }
        service.deleteActivity(a.getId()); assertEquals(5, service.currentUser().getTotalPoints()); assertTrue(service.rewards().isEmpty());
    }
    @Test void goalHistoryAndProgress() throws Exception {
        service.setGoal(1000, 0); add(TODAY, 10, 1000);
        assertEquals(1000, service.stepsOn(TODAY)); assertEquals(10, service.minutesOn(TODAY));
        assertEquals(30, service.currentUser().getTotalPoints());
        service = open(TODAY.plusDays(1)); service.login("alice", "password123".toCharArray()); service.setGoal(2000, 20);
        assertEquals(1000, service.goalOn(TODAY).orElseThrow().getTargetSteps());
        assertEquals(30, service.currentUser().getTotalPoints());
        assertTrue(service.goalOn(TODAY.minusDays(1)).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> service.setGoal(0, 0));
        assertThrows(IllegalArgumentException.class, () -> service.setGoal(-1, 30));
    }
    @Test void sameDayGoalReplacementDoesNotDuplicateBonus() throws Exception {
        add(TODAY, 30, 8000); service.setGoal(8000, 30); service.setGoal(6000, 20);
        assertEquals(50, service.currentUser().getTotalPoints());
        service.setGoal(9000, 30); assertEquals(30, service.currentUser().getTotalPoints());
    }
    @Test void streakUsesUniqueDaysAndYesterdayGrace() throws Exception {
        add(TODAY.minusDays(3), 10, 0); add(TODAY.minusDays(2), 10, 0); add(TODAY.minusDays(1), 10, 0); add(TODAY.minusDays(1), 10, 0);
        assertEquals(3, service.streak().getCurrentStreak()); assertEquals(3, service.streak().getLongestStreak());
        add(TODAY, 10, 0); assertEquals(4, service.streak().getCurrentStreak());
        service = open(TODAY.plusDays(2)); service.login("alice", "password123".toCharArray());
        assertEquals(0, service.streak().getCurrentStreak()); assertEquals(4, service.streak().getLongestStreak());
    }
    @Test void streakGapsBackdatedEditsAndDeletion() throws Exception {
        add(TODAY.minusDays(3), 10, 0); Activity a = add(TODAY.minusDays(1), 10, 0); add(TODAY, 10, 0);
        assertEquals(2, service.streak().getLongestStreak());
        add(TODAY.minusDays(2), 10, 0); assertEquals(4, service.streak().getCurrentStreak());
        service.deleteActivity(a.getId()); assertEquals(1, service.streak().getCurrentStreak()); assertEquals(2, service.streak().getLongestStreak());
    }
    @Test void restartPreservesAccountsActivitiesAndGoals() throws Exception {
        UUID id = add(TODAY, 30, 8000).getId(); service.setGoal(8000, 30);
        service = open(TODAY); service.login("alice", "password123".toCharArray());
        assertEquals(id, service.activities().get(0).getId()); assertEquals(8000, service.goalOn(TODAY).orElseThrow().getTargetSteps());
        assertEquals(50, service.currentUser().getTotalPoints());
        assertFalse(Files.readString(directory.resolve("activa.tsv")).contains("password123"));
    }
    @Test void missingFileAndCorruptFileAreHandledSafely() throws Exception {
        assertFalse(new FileStore(directory.resolve("missing")).load().users.size() > 0);
        Path file = directory.resolve("activa.tsv"); String broken = "ACTIVA\t1\nA\tbroken"; Files.writeString(file, broken);
        assertThrows(IOException.class, () -> open(TODAY)); assertEquals(broken, Files.readString(file));
    }
    @Test void duplicateRecordsAreRejected() throws Exception {
        Path file = directory.resolve("activa.tsv"); List<String> lines = Files.readAllLines(file); lines.add(lines.get(1)); Files.write(file, lines);
        assertThrows(IOException.class, () -> open(TODAY));
    }
    @Test void failedSaveDoesNotAcceptMutation() throws Exception {
        // Simulate an unwritable destination without depending on OS user permissions.
        Path file = directory.resolve("activa.tsv"); Files.delete(file); Files.createDirectory(file);
        assertThrows(IOException.class, () -> add(TODAY, 30, 1000));
        assertTrue(service.activities().isEmpty()); assertEquals(0, service.currentUser().getTotalPoints());
    }
    @Test void textFieldsRoundTripWithoutDelimiters() throws Exception {
        service.register("bobby", "bob|tag@example.com", "with|tabs\t123".toCharArray());
        service = open(TODAY); service.login("bobby", "with|tabs\t123".toCharArray());
        assertEquals("bob|tag@example.com", service.currentUser().getEmail());
    }
    @Test void consoleHandlesBadNumbersDatesIdsAndCustomExceptions() throws Exception {
        String commands = String.join("\n", "2", "alice", "wrong", "2", "alice", "password123", "1", "WALKING", "bad-date",
                "1", "WALKING", TODAY.toString(), "oops", "1", "WALKING", TODAY.toString(), "-1", "0", "0",
                "4", "not-a-uuid", "4", UUID.randomUUID().toString(), "5", "0", "0", "nonsense", "0", "0") + "\n";
        PrintStream original = System.out; ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            new ConsoleUi(service, new ByteArrayInputStream(commands.getBytes(StandardCharsets.UTF_8))).run();
        } finally { System.setOut(original); }
        String text = output.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("Incorrect username")); assertTrue(text.contains("real date"));
        assertTrue(text.contains("valid number")); assertTrue(text.contains("No activity with that ID"));
        assertTrue(text.contains("Set at least one target"));
        service.login("alice", "password123".toCharArray()); assertTrue(service.activities().isEmpty());
    }

    @Test void mainArgumentsDemoAndRestart() throws Exception {
        Path demo = directory.resolve("demo");
        String first = launch(0, "0\n", "--demo", "--data-dir", demo.toString());
        assertTrue(first.contains("Sample data saved"));
        String second = launch(0, "2\ndemo\nActivaDemo123\n2\n7\n8\n9\n0\n0\n", "--demo", "--data-dir", demo.toString());
        assertFalse(second.contains("Sample data saved"));
        assertTrue(second.contains("Current streak: 3")); assertTrue(second.contains("Points: 125"));
        assertTrue(second.contains("All time: 4 activities"));
        assertTrue(launch(0, "", "--help").contains("--data-dir"));
        assertTrue(launch(2, "", "--bad").contains("Unknown option"));
        assertTrue(launch(2, "", "--data-dir").contains("requires a path"));
        Files.writeString(demo.resolve("activa.tsv"), "corrupt");
        assertTrue(launch(1, "", "--data-dir", demo.toString()).contains("Unknown or empty data format"));
    }
    private String launch(int expectedExit, String input, String... args) throws Exception {
        List<String> command = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-cp",
                Path.of("target/classes").toAbsolutePath().toString(), "activa.Main"));
        command.addAll(List.of(args));
        Path log = directory.resolve(UUID.randomUUID() + ".log");
        Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try (OutputStream out = process.getOutputStream()) { out.write(input.getBytes(StandardCharsets.UTF_8)); }
        assertTrue(process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS), "Console process timed out");
        String text = Files.readString(log); assertEquals(expectedExit, process.exitValue(), text); return text;
    }
}
