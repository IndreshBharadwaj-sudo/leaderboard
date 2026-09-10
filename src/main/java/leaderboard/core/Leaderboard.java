package leaderboard.core;

import leaderboard.model.LeaderboardConfig;
import leaderboard.model.RankedEntry;
import leaderboard.model.ScoreEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Scores of a single leaderboard, kept sorted.
 *
 * <p>A HashMap gives the current score of a user in O(1) and a TreeSet keeps everyone ordered.
 * Both are guarded by one lock per leaderboard, so different campaigns never mixed.
 */
public class Leaderboard {

    private final LeaderboardConfig config;
    private final Map<String, ScoreEntry> entryByUser = new HashMap<>();
    private final NavigableSet<ScoreEntry> ranked = new TreeSet<>(ScoreEntry.RANK_ORDER);
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public Leaderboard(LeaderboardConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public LeaderboardConfig getConfig() {
        return config;
    }

    /**
     * Keeps only the best score of the user. Compare and replace happen in the same
     * section, otherwise two concurrent submissions could let a lower score win.
     *
     * @return true if the ranking changed
     */
    public boolean submit(String userId, int score, long sequenceNumber) {
        lock.writeLock().lock();
        try {
            ScoreEntry current = entryByUser.get(userId);
            if (current != null && current.getScore() >= score) {
                return false;
            }
            if (current != null) {
                ranked.remove(current);
            }
            ScoreEntry updated = new ScoreEntry(userId, score, sequenceNumber);
            entryByUser.put(userId, updated);
            ranked.add(updated);
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean hasUser(String userId) {
        lock.readLock().lock();
        try {
            return entryByUser.containsKey(userId);
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<RankedEntry> getAll() {
        return getTop(Integer.MAX_VALUE);
    }

    public List<RankedEntry> getTop(int limit) {
        lock.readLock().lock();
        try {
            List<RankedEntry> result = new ArrayList<>(Math.min(limit, ranked.size()));
            int rank = 1;
            Iterator<ScoreEntry> it = ranked.iterator();
            while (it.hasNext() && result.size() < limit) {
                result.add(toRankedEntry(it.next(), rank++));
            }
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }

    /** Players ranked just below the user. */
    public List<RankedEntry> playersBelow(String userId, int n) {
        lock.readLock().lock();
        try {
            ScoreEntry pivot = entryByUser.get(userId);
            return pivot == null ? List.of() : below(pivot, rankOf(pivot), n);
        } finally {
            lock.readLock().unlock();
        }
    }

    /** Players ranked just above the user, best rank first. */
    public List<RankedEntry> playersAbove(String userId, int n) {
        lock.readLock().lock();
        try {
            ScoreEntry pivot = entryByUser.get(userId);
            return pivot == null ? List.of() : above(pivot, rankOf(pivot), n);
        } finally {
            lock.readLock().unlock();
        }
    }

    /** n above the user, the user, then n below, as one consistent snapshot. */
    public List<RankedEntry> windowAround(String userId, int n) {
        lock.readLock().lock();
        try {
            ScoreEntry pivot = entryByUser.get(userId);
            if (pivot == null) {
                return List.of();
            }
            int pivotRank = rankOf(pivot);
            List<RankedEntry> window = new ArrayList<>(above(pivot, pivotRank, n));
            window.add(toRankedEntry(pivot, pivotRank));
            window.addAll(below(pivot, pivotRank, n));
            return window;
        } finally {
            lock.readLock().unlock();
        }
    }

    public Optional<Integer> rankOf(String userId) {
        lock.readLock().lock();
        try {
            ScoreEntry entry = entryByUser.get(userId);
            return entry == null ? Optional.empty() : Optional.of(rankOf(entry));
        } finally {
            lock.readLock().unlock();
        }
    }

    private List<RankedEntry> below(ScoreEntry pivot, int pivotRank, int n) {
        List<RankedEntry> result = new ArrayList<>();
        int rank = pivotRank + 1;
        Iterator<ScoreEntry> it = ranked.tailSet(pivot, false).iterator();
        while (it.hasNext() && result.size() < n) {
            result.add(toRankedEntry(it.next(), rank++));
        }
        return result;
    }

    private List<RankedEntry> above(ScoreEntry pivot, int pivotRank, int n) {
        List<RankedEntry> result = new ArrayList<>();
        int rank = pivotRank - 1;
        Iterator<ScoreEntry> it = ranked.headSet(pivot, false).descendingIterator();
        while (it.hasNext() && result.size() < n) {
            result.add(toRankedEntry(it.next(), rank--));
        }
        Collections.reverse(result);
        return result;
    }

    private int rankOf(ScoreEntry entry) {
        return ranked.headSet(entry, false).size() + 1;
    }

    private static RankedEntry toRankedEntry(ScoreEntry entry, int rank) {
        return new RankedEntry(rank, entry.getUserId(), entry.getScore());
    }
}