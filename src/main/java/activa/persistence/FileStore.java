package activa.persistence;
import activa.model.*;
import activa.exception.InvalidActivityException;
import activa.service.PasswordHasher;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;

/** One UTF-8 snapshot. Base64 text fields cannot contain our tab/newline delimiters. */
public final class FileStore {
    private final Path file;
    public FileStore(Path directory) { file = directory.resolve("activa.tsv"); }
    private static String encode(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
    private static String decode(String value) throws CharacterCodingException {
        // The decoder rejects malformed UTF-8 instead of silently replacing bytes.
        return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(Base64.getDecoder().decode(value))).toString();
    }
    public AppData load() throws IOException {
        AppData data = new AppData();
        if (!Files.exists(file)) return data;
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        if (lines.isEmpty() || !lines.get(0).equals("ACTIVA\t1")) throw new IOException("Unknown or empty data format: " + file);
        Set<UUID> ids = new HashSet<>(); Set<String> usernames = new HashSet<>(), emails = new HashSet<>();
        Set<String> goalDates = new HashSet<>();
        int lineNumber = 1;
        try {
            for (String line : lines.subList(1, lines.size())) {
                lineNumber++;
                String[] p = line.split("\t", -1);
                UUID id = UUID.fromString(p[1]);
                if (!ids.add(id)) throw new IllegalArgumentException("Duplicate record ID");
                switch (p[0]) {
                    case "U" -> {
                        if (p.length != 5) throw new IllegalArgumentException("User field count");
                        String username = decode(p[2]), email = decode(p[3]), hash = decode(p[4]);
                        if (!username.matches("[A-Za-z0-9_]{3,30}") || email.length() > 254 || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")
                                || !usernames.add(username.toLowerCase(Locale.ROOT)) || !emails.add(email.toLowerCase(Locale.ROOT)))
                            throw new IllegalArgumentException("Invalid or duplicate account");
                        PasswordHasher.matches(new char[0], hash); // Validate format, not credentials.
                        data.users.put(id, new User(id, username, email, hash));
                    }
                    case "A" -> {
                        if (p.length != 9) throw new IllegalArgumentException("Activity field count");
                        data.activities.add(new Activity(id, UUID.fromString(p[2]), ActivityType.valueOf(p[3]),
                                LocalDate.parse(p[4]), Integer.parseInt(p[5]), Integer.parseInt(p[6]), Double.parseDouble(p[7])));
                        if (!p[8].equals("1")) throw new IllegalArgumentException("Activity version");
                    }
                    case "G" -> {
                        if (p.length != 6) throw new IllegalArgumentException("Goal field count");
                        DailyGoal goal = new DailyGoal(id, UUID.fromString(p[2]), LocalDate.parse(p[3]), Integer.parseInt(p[4]), Integer.parseInt(p[5]));
                        // Compare parsed identities, not alternate textual spellings of the same UUID.
                        if (!goalDates.add(goal.getUserId() + ":" + goal.getEffectiveDate()))
                            throw new IllegalArgumentException("Duplicate goal date");
                        data.goals.add(goal);
                    }
                    default -> throw new IllegalArgumentException("Unknown record type");
                }
            }
            for (Activity a : data.activities) if (!data.users.containsKey(a.getUserId())) throw new IllegalArgumentException("Unknown activity owner");
            for (DailyGoal g : data.goals) if (!data.users.containsKey(g.getUserId())) throw new IllegalArgumentException("Unknown goal owner");
        } catch (RuntimeException | InvalidActivityException | CharacterCodingException e) {
            throw new IOException("Invalid data near line " + lineNumber + ". Original file preserved: " + file, e);
        }
        return data;
    }
    public void save(AppData data) throws IOException {
        Files.createDirectories(file.getParent());
        List<String> lines = new ArrayList<>(); lines.add("ACTIVA\t1");
        for (User u : data.users.values()) lines.add(String.join("\t", "U", u.getId().toString(), encode(u.getUsername()), encode(u.getEmail()), encode(u.getPasswordHash())));
        for (Activity a : data.activities) lines.add(String.join("\t", "A", a.getId().toString(), a.getUserId().toString(), a.getType().name(), a.getDate().toString(), "" + a.getDurationMinutes(), "" + a.getSteps(), "" + a.getDistanceKm(), "1"));
        for (DailyGoal g : data.goals) lines.add(String.join("\t", "G", g.getId().toString(), g.getUserId().toString(), g.getEffectiveDate().toString(), "" + g.getTargetSteps(), "" + g.getTargetMinutes()));
        Path temporary = Files.createTempFile(file.getParent(), "activa-", ".tmp");
        try {
            Files.write(temporary, lines, StandardCharsets.UTF_8);
            // Fail safely if this filesystem cannot atomically replace the old snapshot.
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }
}
