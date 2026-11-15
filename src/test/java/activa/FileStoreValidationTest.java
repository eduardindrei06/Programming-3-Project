package activa;

import activa.model.User;
import activa.persistence.AppData;
import activa.persistence.FileStore;
import activa.service.PasswordHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FileStoreValidationTest {
    @TempDir Path directory;
    private FileStore store;
    private Path file;
    private final UUID userId = UUID.fromString("abcdef01-2345-6789-abcd-0123456789ab");

    @BeforeEach void prepareAccount() throws IOException {
        store = new FileStore(directory);
        file = directory.resolve("activa.tsv");
        AppData data = new AppData();
        data.users.put(userId, new User(userId, "alice", "alice@example.com",
                PasswordHasher.hash("password123".toCharArray())));
        store.save(data);
    }

    @Test void rejectsDuplicateGoalDatesWithDifferentlyCasedOwnerIds() throws IOException {
        List<String> lines = Files.readAllLines(file);
        lines.add("G\t" + UUID.randomUUID() + "\t" + userId + "\t2026-11-15\t8000\t30");
        lines.add("G\t" + UUID.randomUUID() + "\t" + userId.toString().toUpperCase(java.util.Locale.ROOT)
                + "\t2026-11-15\t9000\t40");
        Files.write(file, lines);
        assertRejectedWithoutChangingFile();
    }

    @Test void rejectsMalformedUtf8InsideEncodedFields() throws IOException {
        // C3 must be followed by a continuation byte; '(' is not one.
        byte[] malformed = {(byte) 0xc3, 0x28};
        byte[] suffix = "@example.com".getBytes(StandardCharsets.UTF_8);
        byte[] email = new byte[malformed.length + suffix.length];
        System.arraycopy(malformed, 0, email, 0, malformed.length);
        System.arraycopy(suffix, 0, email, malformed.length, suffix.length);
        replaceEmail(Base64.getEncoder().encodeToString(email));
        assertRejectedWithoutChangingFile();
    }

    @Test void rejectsStoredEmailBeyondRegistrationLimit() throws IOException {
        String email = "a".repeat(250) + "@example.com";
        replaceEmail(Base64.getEncoder().encodeToString(email.getBytes(StandardCharsets.UTF_8)));
        assertRejectedWithoutChangingFile();
    }

    @Test void acceptsValidUnicodeAndMaximumLengthEmails() throws IOException {
        for (String email : List.of("élise@example.com", "a".repeat(242) + "@example.com")) {
            replaceEmail(Base64.getEncoder().encodeToString(email.getBytes(StandardCharsets.UTF_8)));
            assertEquals(email, store.load().users.get(userId).getEmail());
        }
    }

    private void replaceEmail(String encodedEmail) throws IOException {
        List<String> lines = Files.readAllLines(file);
        String[] fields = lines.get(1).split("\t", -1);
        fields[3] = encodedEmail;
        lines.set(1, String.join("\t", fields));
        Files.write(file, lines);
    }

    private void assertRejectedWithoutChangingFile() throws IOException {
        byte[] original = Files.readAllBytes(file);
        assertThrows(IOException.class, store::load);
        assertArrayEquals(original, Files.readAllBytes(file));
    }
}
