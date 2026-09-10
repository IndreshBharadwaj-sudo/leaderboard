package leaderboard.repository;

import leaderboard.core.Leaderboard;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** In-memory {@link LeaderboardRepository}. Expired campaigns are kept, never evicted. */
public class InMemoryLeaderboardRepository implements LeaderboardRepository {

    private final Map<String, Leaderboard> byId = new ConcurrentHashMap<>();
    private final Map<String, List<Leaderboard>> byGameId = new ConcurrentHashMap<>();

    @Override
    public void save(Leaderboard leaderboard) {
        String id = leaderboard.getConfig().getLeaderboardId();
        if (byId.putIfAbsent(id, leaderboard) == null) {
            byGameId.computeIfAbsent(leaderboard.getConfig().getGameId(), key -> new CopyOnWriteArrayList<>())
                    .add(leaderboard);
        }
    }

    @Override
    public Optional<Leaderboard> findById(String leaderboardId) {
        return Optional.ofNullable(byId.get(leaderboardId));
    }

    @Override
    public List<Leaderboard> findByGameId(String gameId) {
        return Collections.unmodifiableList(byGameId.getOrDefault(gameId, List.of()));
    }
}