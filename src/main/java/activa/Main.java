package activa;
import activa.model.ActivityType;
import activa.service.ActivaService;
import activa.persistence.FileStore;
import activa.ui.ConsoleUi;
import java.io.IOException;
import java.nio.channels.*;
import java.nio.file.*;
import java.time.Clock;
import java.util.Arrays;

public final class Main {
    private Main() { }
    public static void main(String[] args) {
        try { run(args); }
        catch (IOException e) { System.err.println("Activa could not open/save its data: " + e.getMessage()); System.exit(1); }
        catch (IllegalArgumentException e) { System.err.println("Invalid arguments: " + e.getMessage()); System.exit(2); }
        catch (activa.exception.AccountException | activa.exception.InvalidActivityException e) {
            System.err.println("Demo setup failed: " + e.getMessage()); System.exit(1);
        }
    }
    private static void run(String[] args) throws IOException, activa.exception.AccountException, activa.exception.InvalidActivityException {
        boolean demo = false; Path directory = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--help" -> { System.out.println("java -jar target/activa-0.1.0.jar [--demo] [--data-dir PATH]\nDemo account: demo / ActivaDemo123 (seeded only in an empty directory)."); return; }
                case "--demo" -> demo = true;
                case "--data-dir" -> {
                    if (++i == args.length || args[i].startsWith("--")) throw new IllegalArgumentException("--data-dir requires a path.");
                    directory = Path.of(args[i]);
                }
                default -> throw new IllegalArgumentException("Unknown option: " + args[i] + ". Use --help.");
            }
        }
        if (directory == null) directory = Path.of(demo ? "demo-data" : "data");
        directory = directory.toAbsolutePath().normalize(); Files.createDirectories(directory);
        // The lock is held for the full process to prevent two consoles overwriting each other.
        try (FileChannel channel = FileChannel.open(directory.resolve("activa.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            try (FileLock lock = channel.tryLock()) {
                if (lock == null) throw new IOException("Another Activa process is using " + directory);
                ActivaService service = new ActivaService(new FileStore(directory), Clock.systemDefaultZone());
                if (demo && !service.hasUsers()) {
                    char[] password = "ActivaDemo123".toCharArray();
                    try {
                        service.register("demo", "demo@example.com", password); service.login("demo", password);
                        service.setGoal(8000, 30);
                        service.saveActivity(null, ActivityType.WALKING, service.today().minusDays(2), 30, 6000, 4);
                        service.saveActivity(null, ActivityType.RUNNING, service.today().minusDays(1), 25, 4000, 4);
                        service.saveActivity(null, ActivityType.WALKING, service.today(), 30, 8000, 5);
                        service.saveActivity(null, ActivityType.WORKOUT, service.today(), 20, 0, 0);
                        service.logout();
                    } finally { Arrays.fill(password, '\0'); }
                    System.out.println("Sample data saved. Login: demo / ActivaDemo123");
                }
                System.out.println("Data directory: " + directory);
                new ConsoleUi(service, System.in).run();
            }
        }
    }
}
