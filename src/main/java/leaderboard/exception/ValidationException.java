package leaderboard.exception;

/** Thrown when caller input violates a business rule. */
public class ValidationException extends LeaderboardException {

    public ValidationException(String message) {
        super(message);
    }
}