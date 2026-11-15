package activa.persistence;
import activa.model.*;
import java.util.*;
/** Collections owned by one service instance; copies make saving transactional. */
public final class AppData {
    public final HashMap<UUID, User> users = new HashMap<>();
    public final ArrayList<Activity> activities = new ArrayList<>();
    public final ArrayList<DailyGoal> goals = new ArrayList<>();
    public AppData copy() {
        AppData copy = new AppData(); copy.users.putAll(users);
        copy.activities.addAll(activities); copy.goals.addAll(goals); return copy;
    }
}
