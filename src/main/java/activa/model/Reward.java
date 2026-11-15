package activa.model;
/** Immutable shared badge catalog entry; unlocked badges are derived from current points. */
public record Reward(int id, String name, long pointsRequired) { }
