package activa.interfaces;
/** Evaluates the totals recorded on one day against a goal. */
public interface GoalEvaluable {
    boolean isComplete(long steps, long minutes);
}
