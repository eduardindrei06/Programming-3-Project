package activa.model;
import activa.interfaces.GoalEvaluable;
import java.time.LocalDate;
import java.util.UUID;
/** A goal version applies from effectiveDate until the next version. Zero disables a target. */
public final class DailyGoal implements GoalEvaluable {
    private final UUID id, userId;
    private final LocalDate effectiveDate;
    private final int targetSteps, targetMinutes;
    public DailyGoal(UUID id, UUID userId, LocalDate effectiveDate, int targetSteps, int targetMinutes) {
        if (targetSteps < 0 || targetSteps > 200000 || targetMinutes < 0 || targetMinutes > 1440
                || (targetSteps == 0 && targetMinutes == 0)) {
            throw new IllegalArgumentException("Set at least one target: 0–200000 steps, 0–1440 minutes (0 disables it).");
        }
        this.id = java.util.Objects.requireNonNull(id); this.userId = java.util.Objects.requireNonNull(userId);
        this.effectiveDate = java.util.Objects.requireNonNull(effectiveDate);
        this.targetSteps = targetSteps; this.targetMinutes = targetMinutes;
    }
    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public LocalDate getEffectiveDate() { return effectiveDate; }
    public int getTargetSteps() { return targetSteps; }
    public int getTargetMinutes() { return targetMinutes; }
    @Override public boolean isComplete(long steps, long minutes) {
        return steps >= targetSteps && minutes >= targetMinutes;
    }
}
