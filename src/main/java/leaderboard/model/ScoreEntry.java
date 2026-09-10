package leaderboard.model;

import java.util.Comparator;
import java.util.Objects;

/**
 * The best score a user has on a leaderboard. Ordered by score descending, then by who got there
 * first; userId is the last tie breaker so the comparator stays a total order for the TreeSet.
 */
public final class ScoreEntry {

    public static final Comparator<ScoreEntry> RANK_ORDER = Comparator
            .comparingInt(ScoreEntry::getScore).reversed()
            .thenComparingLong(ScoreEntry::getSequenceNumber)
            .thenComparing(ScoreEntry::getUserId);

    private final String userId;
    private final int score;
    private final long sequenceNumber;

    public ScoreEntry(String userId, int score, long sequenceNumber) {
        this.userId = Objects.requireNonNull(userId, "userId");
        this.score = score;
        this.sequenceNumber = sequenceNumber;
    }

    public String getUserId() {
        return userId;
    }

    public int getScore() {
        return score;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ScoreEntry other)) {
            return false;
        }
        return score == other.score && sequenceNumber == other.sequenceNumber && userId.equals(other.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, score, sequenceNumber);
    }

    @Override
    public String toString() {
        return userId + "=" + score;
    }
}