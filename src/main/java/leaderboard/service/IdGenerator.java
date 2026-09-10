package leaderboard.service;

/** Injected so tests can use predictable leaderboard ids. */
@FunctionalInterface
public interface IdGenerator {

    String newId();
}