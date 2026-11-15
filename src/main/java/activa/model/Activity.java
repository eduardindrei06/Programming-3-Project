package activa.model;
import activa.interfaces.PointAwardable;
import activa.exception.InvalidActivityException;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Immutable activity: editing replaces a record while preserving its ID. */
public final class Activity implements PointAwardable {
    private final UUID id, userId;
    private final ActivityType type;
    private final LocalDate date;
    private final int durationMinutes, steps;
    private final double distanceKm;
    public Activity(UUID id, UUID userId, ActivityType type, LocalDate date,
                    int durationMinutes, int steps, double distanceKm) throws InvalidActivityException {
        if (date == null || durationMinutes < 1 || durationMinutes > 1440 || steps < 0 || steps > 200000
                || !Double.isFinite(distanceKm) || distanceKm < 0 || distanceKm > 1000 || type == null) {
            throw new InvalidActivityException("Use a date, type, 1–1440 minutes, 0–200000 steps and 0–1000 km.");
        }
        this.id = Objects.requireNonNull(id); this.userId = Objects.requireNonNull(userId);
        this.type = type; this.date = date; this.durationMinutes = durationMinutes;
        this.steps = steps; this.distanceKm = distanceKm;
    }
    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public ActivityType getType() { return type; }
    public LocalDate getDate() { return date; }
    public int getDurationMinutes() { return durationMinutes; }
    public int getSteps() { return steps; }
    public double getDistanceKm() { return distanceKm; }
    @Override public long calculatePoints() { return durationMinutes; }
    @Override public String toString() {
        return id + " | " + date + " | " + type + " | " + durationMinutes + " min | " + steps + " steps | " + distanceKm + " km";
    }
}
