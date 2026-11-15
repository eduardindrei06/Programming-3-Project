package activa.model;
import java.time.LocalDate;
import java.util.Collection;
import java.util.TreeSet;
import java.util.UUID;
public final class Streak {
    private final UUID userId;
    private final int currentStreak, longestStreak;
    public Streak(UUID userId, Collection<LocalDate> dates, LocalDate today) {
        this.userId = userId;
        TreeSet<LocalDate> days = new TreeSet<>(dates);
        days.removeIf(day -> day.isAfter(today));
        int run = 0, longest = 0;
        LocalDate previous = null;
        for (LocalDate day : days) {
            run = previous != null && previous.plusDays(1).equals(day) ? run + 1 : 1;
            longest = Math.max(longest, run); previous = day;
        }
        // Yesterday's streak remains current until today ends without an activity.
        currentStreak = previous != null && !previous.isBefore(today.minusDays(1)) ? run : 0;
        longestStreak = longest;
    }
    public UUID getUserId() { return userId; }
    public int getCurrentStreak() { return currentStreak; }
    public int getLongestStreak() { return longestStreak; }
}
