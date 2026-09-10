package leaderboard.repository;

import leaderboard.core.Leaderboard;

import java.util.List;
import java.util.Optional;

/** Data access for leaderboards, finished ones included. */
public interface LeaderboardRepository {

    void save(Leaderboard leaderboard);

    Optional<Leaderboard> findById(String leaderboardId);

    List<Leaderboard> findByGameId(String gameId);
}