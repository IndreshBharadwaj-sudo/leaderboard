package leaderboard.model;

import java.util.Objects;

/** A game that can host leaderboards. */
public final class Game {

    private final String gameId;
    private final String name;

    public Game(String gameId, String name) {
        this.gameId = Objects.requireNonNull(gameId, "gameId");
        this.name = Objects.requireNonNull(name, "name");
    }

    public String getGameId() {
        return gameId;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return "Game{" + gameId + ", " + name + '}';
    }
}