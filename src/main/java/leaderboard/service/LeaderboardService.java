package leaderboard.service;

import leaderboard.model.LeaderboardConfig;
import leaderboard.model.RankedEntry;

import java.util.List;
import java.util.Optional;

/** Public API of the leaderboard system. */
public interface LeaderboardService {

    void registerGame(String gameId, String name);

    List<String> getSupportedGames();

    /** Creates a campaign for a game and returns its leaderboard id. */
    String createLeaderboard(String gameId, long startEpochSeconds, long endEpochSeconds);

    LeaderboardConfig getLeaderboardConfig(String leaderboardId);

    /** Every campaign of a game, finished ones included. */
    List<LeaderboardConfig> getLeaderboardsOfGame(String gameId);

    List<LeaderboardConfig> getActiveLeaderboards(String gameId);

    /** Full ranking, best score first. Still readable once the campaign has ended. */
    List<RankedEntry> getLeaderboard(String leaderboardId);

    List<RankedEntry> getTopScorers(String leaderboardId, int count);

    /**
     * Applies the score to every campaign of the game that is active now.
     *
     * @return ids of the leaderboards whose ranking changed
     */
    List<String> submitScore(String gameId, String userId, int score);

    /** The nPlayers ranked below the user. */
    List<RankedEntry> listPlayersNext(String gameId, String leaderboardId, String userId, int nPlayers);

    /** The nPlayers ranked above the user, best rank first. */
    List<RankedEntry> listPlayersPrev(String gameId, String leaderboardId, String userId, int nPlayers);

    /** nPlayers above and nPlayers below the user, with the user in the middle. */
    List<RankedEntry> listPlayersAround(String gameId, String leaderboardId, String userId, int nPlayers);

    Optional<Integer> getRank(String leaderboardId, String userId);

    /**
     * Winner of a finished campaign, empty if nobody scored.
     *
     * @throws leaderboard.exception.ValidationException if the campaign is still running
     */
    Optional<RankedEntry> getWinner(String leaderboardId);
}