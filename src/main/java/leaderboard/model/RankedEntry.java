package leaderboard.model;

import java.util.Objects;

/** A leaderboard row exposed to clients: rank (1-based), user and score. */
public final class RankedEntry {

    private final int rank;
    private final String userId;
    private final int score;

    public RankedEntry(int rank, String userId, int score) {
        this.rank = rank;
        this.userId = Objects.requireNonNull(userId, "userId");
        this.score = score;
    }

    public int getRank() {
        return rank;
    }

    public String getUserId() {
        return userId;
    }

    public int getScore() {
        return score;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RankedEntry other)) {
            return false;
        }
        return rank == other.rank && score == other.score && userId.equals(other.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rank, userId, score);
    }

    @Override
    public String toString() {
        return "#" + rank + " " + userId + " (" + score + ")";
    }
}