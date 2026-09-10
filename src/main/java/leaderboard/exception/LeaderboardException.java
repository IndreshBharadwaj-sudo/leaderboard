package leaderboard.exception;

/** Base type for all leaderboard domain failures. */
public abstract class LeaderboardException extends RuntimeException {

    protected LeaderboardException(String message) {
        super(message);
    }
}