package activa.service;
import activa.exception.*;
import activa.interfaces.*;
import activa.model.*;
import activa.persistence.*;
import java.io.IOException;
import java.time.*;
import java.util.*;

/** Business rules independent of console input. Login selects the only accessible user. */
public final class ActivaService {
    private final FileStore store;
    private final Clock clock;
    private AppData data;
    private UUID currentUserId;
    private static final List<Reward> CATALOG = List.of(new Reward(1, "First Steps", 30),
            new Reward(2, "Building Momentum", 150), new Reward(3, "Activa Champion", 500));
    public ActivaService(FileStore store, Clock clock) throws IOException {
        this.store = store; this.clock = clock; data = store.load();
        for (Activity a : data.activities) if (a.getDate().isAfter(today())) throw new IOException("Stored activity has a future date; data preserved.");
        refreshPoints();
    }
    public LocalDate today() { return LocalDate.now(clock); }
    public boolean hasUsers() { return !data.users.isEmpty(); }
    public User register(String username, String email, char[] password) throws AccountException, IOException {
        username = username.trim(); email = email.trim();
        if (!username.matches("[A-Za-z0-9_]{3,30}")) throw new AccountException("Username needs 3–30 letters, digits or underscores.");
        if (email.length() > 254 || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw new AccountException("Enter a valid email address.");
        if (password.length < 8 || password.length > 128) throw new AccountException("Password needs 8–128 characters.");
        for (User u : data.users.values()) if (u.getUsername().equalsIgnoreCase(username) || u.getEmail().equalsIgnoreCase(email))
            throw new AccountException("Username or email is already registered.");
        User user = new User(UUID.randomUUID(), username, email, PasswordHasher.hash(password));
        AppData next = data.copy(); next.users.put(user.getId(), user); commit(next); return user;
    }
    public void login(String username, char[] password) throws AccountException {
        for (User u : data.users.values()) {
            if (u.getUsername().equalsIgnoreCase(username.trim()) && PasswordHasher.matches(password, u.getPasswordHash())) {
                currentUserId = u.getId(); return;
            }
        }
        throw new AccountException("Incorrect username or password.");
    }
    public void logout() { currentUserId = null; }
    private UUID owner() {
        if (currentUserId == null) throw new IllegalStateException("Log in first.");
        return currentUserId;
    }
    public User currentUser() { return data.users.get(owner()); }
    public List<Activity> activities() {
        UUID id = owner();
        return data.activities.stream().filter(a -> a.getUserId().equals(id))
                .sorted(Comparator.comparing(Activity::getDate).thenComparing(Activity::getId)).toList();
    }
    public Activity saveActivity(UUID id, ActivityType type, LocalDate date, int minutes, int steps, double km)
            throws InvalidActivityException, IOException {
        UUID userId = owner();
        if (date == null || date.isAfter(today())) throw new InvalidActivityException("Activity date cannot be in the future.");
        if (id != null) findActivity(id);
        Activity a = new Activity(id == null ? UUID.randomUUID() : id, userId, type, date, minutes, steps, km);
        AppData next = data.copy(); next.activities.removeIf(old -> old.getId().equals(a.getId())); next.activities.add(a);
        commit(next); return a;
    }
    private Activity findActivity(UUID id) throws InvalidActivityException {
        return activities().stream().filter(a -> a.getId().equals(id)).findFirst()
                .orElseThrow(() -> new InvalidActivityException("No activity with that ID belongs to you."));
    }
    public void deleteActivity(UUID id) throws InvalidActivityException, IOException {
        findActivity(id); AppData next = data.copy(); next.activities.removeIf(a -> a.getId().equals(id)); commit(next);
    }
    public void setGoal(int steps, int minutes) throws IOException {
        DailyGoal goal = new DailyGoal(UUID.randomUUID(), owner(), today(), steps, minutes);
        AppData next = data.copy();
        next.goals.removeIf(g -> g.getUserId().equals(owner()) && g.getEffectiveDate().equals(today()));
        next.goals.add(goal); commit(next);
    }
    public Optional<DailyGoal> goalOn(LocalDate date) {
        UUID id = owner();
        return data.goals.stream().filter(g -> g.getUserId().equals(id) && !g.getEffectiveDate().isAfter(date))
                .max(Comparator.comparing(DailyGoal::getEffectiveDate));
    }
    public long stepsOn(LocalDate date) { return activities().stream().filter(a -> a.getDate().equals(date)).mapToLong(Activity::getSteps).sum(); }
    public long minutesOn(LocalDate date) { return activities().stream().filter(a -> a.getDate().equals(date)).mapToLong(Activity::getDurationMinutes).sum(); }
    public Streak streak() { return new Streak(owner(), activities().stream().map(Activity::getDate).toList(), today()); }
    public List<Reward> rewards() { return CATALOG.stream().filter(r -> currentUser().getTotalPoints() >= r.pointsRequired()).toList(); }
    private void commit(AppData next) throws IOException {
        store.save(next); data = next; refreshPoints();
    }
    private void refreshPoints() {
        // Never increment saved totals: derive from source records once per change/load.
        for (User user : data.users.values()) {
            long points = 0;
            Map<LocalDate, long[]> totals = new HashMap<>();
            for (Activity a : data.activities) if (a.getUserId().equals(user.getId())) {
                PointAwardable awardable = a; points += awardable.calculatePoints();
                long[] total = totals.computeIfAbsent(a.getDate(), d -> new long[2]);
                total[0] += a.getSteps(); total[1] += a.getDurationMinutes();
            }
            for (Map.Entry<LocalDate, long[]> day : totals.entrySet()) {
                Optional<DailyGoal> goal = data.goals.stream().filter(g -> g.getUserId().equals(user.getId())
                        && !g.getEffectiveDate().isAfter(day.getKey())).max(Comparator.comparing(DailyGoal::getEffectiveDate));
                if (goal.isPresent()) {
                    GoalEvaluable evaluable = goal.get();
                    if (evaluable.isComplete(day.getValue()[0], day.getValue()[1])) points += 20;
                }
            }
            user.setTotalPoints(points);
        }
    }
}
