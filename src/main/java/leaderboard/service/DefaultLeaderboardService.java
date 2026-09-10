package leaderboard.service;

import leaderboard.core.Leaderboard;
import leaderboard.exception.NotFoundException;
import leaderboard.exception.ValidationException;
import leaderboard.model.Game;
import leaderboard.model.LeaderboardConfig;
import leaderboard.model.RankedEntry;
import leaderboard.repository.GameRepository;
import leaderboard.repository.LeaderboardRepository;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Business logic of the leaderboard system. Storage sits behind the two repositories and the
 * current time behind an injected {@link Clock}, which keeps this class testable.
 */
public class DefaultLeaderboardService implements LeaderboardService {

    private static final int MIN_SCORE = 0;
    private static final int MAX_SCORE = 1_000_000_000;

    private final GameRepository gameRepository;
    private final LeaderboardRepository leaderboardRepository;
    private final Clock clock;
    private final IdGenerator idGenerator;

    // Breaks score ties: whoever reached the score first ranks higher.
    private final AtomicLong submissionSequence = new AtomicLong();

    public DefaultLeaderboardService(GameRepository gameRepository,
                                     LeaderboardRepository leaderboardRepository,
                                     Clock clock,
                                     IdGenerator idGenerator) {
        this.gameRepository = Objects.requireNonNull(gameRepository, "gameRepository");
        this.leaderboardRepository = Objects.requireNonNull(leaderboardRepository, "leaderboardRepository");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
    }

    @Override
    public void registerGame(String gameId, String name) {
        requireText(gameId, "gameId");
        requireText(name, "name");
        if (!gameRepository.saveIfAbsent(new Game(gameId, name))) {
            throw new ValidationException("Game already registered: " + gameId);
        }
    }

    @Override
    public List<String> getSupportedGames() {
        return gameRepository.findAll().stream().map(Game::getGameId).toList();
    }

    @Override
    public String createLeaderboard(String gameId, long startEpochSeconds, long endEpochSeconds) {
        requireGame(gameId);
        if (endEpochSeconds <= nowEpochSeconds()) {
            throw new ValidationException("Cannot create a leaderboard that has already ended");
        }
        LeaderboardConfig config =
                new LeaderboardConfig(idGenerator.newId(), gameId, startEpochSeconds, endEpochSeconds);
        leaderboardRepository.save(new Leaderboard(config));
        return config.getLeaderboardId();
    }

    @Override
    public LeaderboardConfig getLeaderboardConfig(String leaderboardId) {
        return requireLeaderboard(leaderboardId).getConfig();
    }

    @Override
    public List<LeaderboardConfig> getLeaderboardsOfGame(String gameId) {
        requireGame(gameId);
        return leaderboardRepository.findByGameId(gameId).stream().map(Leaderboard::getConfig).toList();
    }

    @Override
    public List<LeaderboardConfig> getActiveLeaderboards(String gameId) {
        long now = nowEpochSeconds();
        return getLeaderboardsOfGame(gameId).stream().filter(config -> config.isActiveAt(now)).toList();
    }

    @Override
    public List<RankedEntry> getLeaderboard(String leaderboardId) {
        return requireLeaderboard(leaderboardId).getAll();
    }

    @Override
    public List<RankedEntry> getTopScorers(String leaderboardId, int count) {
        requirePositive(count, "count");
        return requireLeaderboard(leaderboardId).getTop(count);
    }

    @Override
    public List<String> submitScore(String gameId, String userId, int score) {
        requireText(userId, "userId");
        requireScore(score);
        requireGame(gameId);

        // Read the clock once so the score is judged against every campaign at the same instant.
        long now = nowEpochSeconds();
        long sequenceNumber = submissionSequence.incrementAndGet();

        List<String> updatedLeaderboards = new ArrayList<>();
        for (Leaderboard leaderboard : leaderboardRepository.findByGameId(gameId)) {
            if (leaderboard.getConfig().isActiveAt(now) && leaderboard.submit(userId, score, sequenceNumber)) {
                updatedLeaderboards.add(leaderboard.getConfig().getLeaderboardId());
            }
        }
        return updatedLeaderboards;
    }

    @Override
    public List<RankedEntry> listPlayersNext(String gameId, String leaderboardId, String userId, int nPlayers) {
        Leaderboard leaderboard = requireParticipant(gameId, leaderboardId, userId, nPlayers);
        return leaderboard.playersBelow(userId, nPlayers);
    }

    @Override
    public List<RankedEntry> listPlayersPrev(String gameId, String leaderboardId, String userId, int nPlayers) {
        Leaderboard leaderboard = requireParticipant(gameId, leaderboardId, userId, nPlayers);
        return leaderboard.playersAbove(userId, nPlayers);
    }

    @Override
    public List<RankedEntry> listPlayersAround(String gameId, String leaderboardId, String userId, int nPlayers) {
        Leaderboard leaderboard = requireParticipant(gameId, leaderboardId, userId, nPlayers);
        return leaderboard.windowAround(userId, nPlayers);
    }

    @Override
    public Optional<Integer> getRank(String leaderboardId, String userId) {
        requireText(userId, "userId");
        return requireLeaderboard(leaderboardId).rankOf(userId);
    }

    @Override
    public Optional<RankedEntry> getWinner(String leaderboardId) {
        Leaderboard leaderboard = requireLeaderboard(leaderboardId);
        if (!leaderboard.getConfig().hasEndedAt(nowEpochSeconds())) {
            throw new ValidationException("Leaderboard is still running: " + leaderboardId);
        }
        return leaderboard.getTop(1).stream().findFirst();
    }

    private long nowEpochSeconds() {
        return clock.instant().getEpochSecond();
    }

    private void requireGame(String gameId) {
        requireText(gameId, "gameId");
        if (!gameRepository.exists(gameId)) {
            throw new NotFoundException("Unknown game: " + gameId);
        }
    }

    private Leaderboard requireLeaderboard(String leaderboardId) {
        requireText(leaderboardId, "leaderboardId");
        return leaderboardRepository.findById(leaderboardId)
                .orElseThrow(() -> new NotFoundException("Unknown leaderboard: " + leaderboardId));
    }

    private Leaderboard requireParticipant(String gameId, String leaderboardId, String userId, int nPlayers) {
        requireText(userId, "userId");
        requirePositive(nPlayers, "nPlayers");
        requireGame(gameId);

        Leaderboard leaderboard = requireLeaderboard(leaderboardId);
        if (!leaderboard.getConfig().getGameId().equals(gameId)) {
            throw new ValidationException(
                    "Leaderboard " + leaderboardId + " does not belong to game " + gameId);
        }
        if (!leaderboard.hasUser(userId)) {
            throw new NotFoundException(
                    "User " + userId + " has no score on leaderboard " + leaderboardId);
        }
        return leaderboard;
    }

    private static void requireScore(int score) {
        if (score < MIN_SCORE || score > MAX_SCORE) {
            throw new ValidationException(
                    "score must be between " + MIN_SCORE + " and " + MAX_SCORE + " but was " + score);
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(field + " must not be blank");
        }
    }

    private static void requirePositive(int value, String field) {
        if (value <= 0) {
            throw new ValidationException(field + " must be positive but was " + value);
        }
    }
}