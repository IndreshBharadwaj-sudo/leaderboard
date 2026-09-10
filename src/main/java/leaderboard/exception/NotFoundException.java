package leaderboard.exception;

/** Thrown when a game, leaderboard or user cannot be located. */
public class NotFoundException extends LeaderboardException {

    public NotFoundException(String message) {
        super(message);
    }
}