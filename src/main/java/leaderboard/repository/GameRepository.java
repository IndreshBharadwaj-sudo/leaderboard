package leaderboard.repository;

import leaderboard.model.Game;

import java.util.List;

/** Data access for games. */
public interface GameRepository {

    /** @return false if a game with the same id already exists */
    boolean saveIfAbsent(Game game);

    boolean exists(String gameId);

    /** All games, ordered by id. */
    List<Game> findAll();
}