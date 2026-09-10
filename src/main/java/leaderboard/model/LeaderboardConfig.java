package leaderboard.model;

import leaderboard.exception.ValidationException;

import java.util.Objects;

/** Campaign window of a leaderboard. */
public final class LeaderboardConfig {

    private final String leaderboardId;
    private final String gameId;
    private final long startEpochSeconds;
    private final long endEpochSeconds;

    public LeaderboardConfig(String leaderboardId, String gameId, long startEpochSeconds, long endEpochSeconds) {
        this.leaderboardId = Objects.requireNonNull(leaderboardId, "leaderboardId");
        this.gameId = Objects.requireNonNull(gameId, "gameId");
        if (endEpochSeconds <= startEpochSeconds) {
            throw new ValidationException("endEpochSeconds must be after startEpochSeconds");
        }
        this.startEpochSeconds = startEpochSeconds;
        this.endEpochSeconds = endEpochSeconds;
    }

    public String getLeaderboardId() {
        return leaderboardId;
    }

    public String getGameId() {
        return gameId;
    }

    public long getStartEpochSeconds() {
        return startEpochSeconds;
    }

    public long getEndEpochSeconds() {
        return endEpochSeconds;
    }

    /** Window is [start, end), so a score at the end instant no longer counts. */
    public boolean isActiveAt(long epochSeconds) {
        return epochSeconds >= startEpochSeconds && epochSeconds < endEpochSeconds;
    }

    public boolean hasEndedAt(long epochSeconds) {
        return epochSeconds >= endEpochSeconds;
    }

    @Override
    public String toString() {
        return "LeaderboardConfig{" + leaderboardId + ", game=" + gameId
                + ", [" + startEpochSeconds + ", " + endEpochSeconds + ")}";
    }
}