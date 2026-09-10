package leaderboard.repository;

import leaderboard.model.Game;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory {@link GameRepository}. */
public class InMemoryGameRepository implements GameRepository {

    private final Map<String, Game> games = new ConcurrentHashMap<>();

    @Override
    public boolean saveIfAbsent(Game game) {
        return games.putIfAbsent(game.getGameId(), game) == null;
    }

    @Override
    public boolean exists(String gameId) {
        return games.containsKey(gameId);
    }

    @Override
    public List<Game> findAll() {
        List<Game> all = new ArrayList<>(games.values());
        all.sort(Comparator.comparing(Game::getGameId));
        return List.copyOf(all);
    }
}