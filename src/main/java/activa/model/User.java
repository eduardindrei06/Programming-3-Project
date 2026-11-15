package activa.model;
import java.util.UUID;
public final class User {
    private final UUID id;
    private final String username, email, passwordHash;
    private long totalPoints;
    public User(UUID id, String username, String email, String passwordHash) {
        this.id = id; this.username = username; this.email = email; this.passwordHash = passwordHash;
    }
    public UUID getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public long getTotalPoints() { return totalPoints; }
    public void setTotalPoints(long points) { totalPoints = points; }
}
