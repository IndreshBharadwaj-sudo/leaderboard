package leaderboard;

import leaderboard.exception.NotFoundException;
import leaderboard.exception.ValidationException;
import leaderboard.model.RankedEntry;
import leaderboard.repository.InMemoryGameRepository;
import leaderboard.repository.InMemoryLeaderboardRepository;
import leaderboard.service.DefaultLeaderboardService;
import leaderboard.service.LeaderboardService;
import leaderboard.service.UuidIdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeaderboardServiceTest {

    private static final String GAME = "bubble-shooter";
    private static final String OTHER_GAME = "carrom";
    private static final long START = 1_000_000L;

    private MutableClock clock;
    private LeaderboardService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.ofEpochSecond(START));
        service = new DefaultLeaderboardService(
                new InMemoryGameRepository(),
                new InMemoryLeaderboardRepository(),
                clock,
                new UuidIdGenerator());
        service.registerGame(GAME, "Bubble Shooter");
        service.registerGame(OTHER_GAME, "Carrom");
    }

    private String createLeaderboard(String gameId, long startOffset, long endOffset) {
        return service.createLeaderboard(gameId, START + startOffset, START + endOffset);
    }

    private static List<String> users(List<RankedEntry> entries) {
        return entries.stream().map(RankedEntry::getUserId).toList();
    }

    @Test
    void supportsMultipleGames() {
        assertEquals(List.of(GAME, OTHER_GAME), service.getSupportedGames());
    }

    @Test
    void scoreGoesOnlyToActiveLeaderboardsOfTheGame() {
        String daily = createLeaderboard(GAME, 0, 100);
        String weekly = createLeaderboard(GAME, 0, 1_000);
        String notStarted = createLeaderboard(GAME, 500, 900);
        String otherGameBoard = createLeaderboard(OTHER_GAME, 0, 1_000);

        assertEquals(2, service.getActiveLeaderboards(GAME).size());
        assertEquals(3, service.getLeaderboardsOfGame(GAME).size());

        service.submitScore(GAME, "alice", 500);

        assertEquals(List.of(new RankedEntry(1, "alice", 500)), service.getLeaderboard(daily));
        assertEquals(List.of(new RankedEntry(1, "alice", 500)), service.getLeaderboard(weekly));
        assertTrue(service.getLeaderboard(notStarted).isEmpty());
        assertTrue(service.getLeaderboard(otherGameBoard).isEmpty());
    }

    @Test
    void scoreIsDroppedWhenNoLeaderboardIsActive() {
        createLeaderboard(GAME, 500, 900);
        assertEquals(List.of(), service.submitScore(GAME, "alice", 500));
    }

    @Test
    void onlyBestScorePerUserIsKept() {
        String daily = createLeaderboard(GAME, 0, 100);

        service.submitScore(GAME, "alice", 500);
        assertEquals(List.of(), service.submitScore(GAME, "alice", 400));
        assertEquals(List.of(), service.submitScore(GAME, "alice", 500));
        assertEquals(List.of(daily), service.submitScore(GAME, "alice", 900));

        assertEquals(List.of(new RankedEntry(1, "alice", 900)), service.getLeaderboard(daily));
    }

    @Test
    void ranksHighToLowAndBreaksTiesByWhoScoredFirst() {
        String daily = createLeaderboard(GAME, 0, 100);

        service.submitScore(GAME, "alice", 500);
        service.submitScore(GAME, "bob", 500);
        service.submitScore(GAME, "carol", 900);

        assertEquals(List.of("carol", "alice", "bob"), users(service.getLeaderboard(daily)));
        assertEquals(2, service.getRank(daily, "alice").orElseThrow());
    }

    @Test
    void expiredLeaderboardStopsAcceptingScoresButStaysReadable() {
        String daily = createLeaderboard(GAME, 0, 100);
        String weekly = createLeaderboard(GAME, 0, 1_000);
        service.submitScore(GAME, "alice", 500);

        clock.advanceSeconds(200);
        service.submitScore(GAME, "bob", 800);

        assertEquals(List.of("alice"), users(service.getLeaderboard(daily)));
        assertEquals(List.of("bob", "alice"), users(service.getLeaderboard(weekly)));
        assertEquals(new RankedEntry(1, "alice", 500), service.getWinner(daily).orElseThrow());
        assertThrows(ValidationException.class, () -> service.getWinner(weekly));
    }

    @Test
    void topScorersAreCappedAtRequestedCount() {
        String daily = createLeaderboard(GAME, 0, 100);
        service.submitScore(GAME, "alice", 100);
        service.submitScore(GAME, "bob", 300);
        service.submitScore(GAME, "carol", 200);

        assertEquals(List.of("bob", "carol"), users(service.getTopScorers(daily, 2)));
        assertEquals(3, service.getTopScorers(daily, 50).size());
        assertTrue(service.getTopScorers(createLeaderboard(GAME, 0, 100), 5).isEmpty());
    }

    @Test
    void neighbourWindowsAroundAUser() {
        String daily = createLeaderboard(GAME, 0, 100);
        // u0..u4 with scores 500, 400, 300, 200, 100
        IntStream.range(0, 5).forEach(i -> service.submitScore(GAME, "u" + i, 500 - i * 100));

        assertEquals(List.of("u3", "u4"), users(service.listPlayersNext(GAME, daily, "u2", 2)));
        assertEquals(List.of("u0", "u1"), users(service.listPlayersPrev(GAME, daily, "u2", 2)));
        assertEquals(List.of("u1", "u2", "u3"), users(service.listPlayersAround(GAME, daily, "u2", 1)));

        // fewer neighbours than requested at the edges, and a window wider than the board
        assertEquals(List.of(), users(service.listPlayersPrev(GAME, daily, "u0", 3)));
        assertEquals(List.of(), users(service.listPlayersNext(GAME, daily, "u4", 3)));
        assertEquals(List.of("u3", "u4"), users(service.listPlayersAround(GAME, daily, "u4", 1)));
        assertEquals(List.of("u0", "u1", "u2", "u3", "u4"),
                users(service.listPlayersAround(GAME, daily, "u2", 99)));
    }

    @Test
    void validationsAndNotFoundCases() {
        String daily = createLeaderboard(GAME, 0, 100);
        service.submitScore(GAME, "alice", 10);

        assertThrows(NotFoundException.class, () -> service.submitScore("unknown-game", "alice", 10));
        assertThrows(ValidationException.class, () -> service.submitScore(GAME, "alice", -1));
        assertThrows(ValidationException.class, () -> service.submitScore(GAME, "alice", 1_000_000_001));
        assertThrows(ValidationException.class, () -> service.submitScore(GAME, " ", 10));
        assertThrows(ValidationException.class, () -> service.submitScore(GAME, null, 10));
        assertThrows(NotFoundException.class, () -> service.getLeaderboard("no-such-board"));
        assertThrows(ValidationException.class, () -> service.getTopScorers(daily, 0));
        assertThrows(ValidationException.class, () -> service.createLeaderboard(GAME, START + 10, START + 10));
        assertThrows(ValidationException.class, () -> service.createLeaderboard(GAME, START - 100, START - 50));
        assertThrows(ValidationException.class, () -> service.registerGame(GAME, "duplicate"));
        assertThrows(NotFoundException.class, () -> service.getLeaderboardsOfGame("unknown-game"));
        assertThrows(ValidationException.class, () -> service.listPlayersNext(OTHER_GAME, daily, "alice", 1));
        assertThrows(ValidationException.class, () -> service.listPlayersNext(GAME, daily, "alice", 0));
        assertThrows(NotFoundException.class, () -> service.listPlayersNext(GAME, daily, "ghost", 1));
        assertTrue(service.getRank(daily, "ghost").isEmpty());
    }

    @Test
    void concurrentSubmissionsKeepTheHighestScoreExactlyOncePerUser() throws Exception {
        String daily = createLeaderboard(GAME, 0, 10_000);
        String weekly = createLeaderboard(GAME, 0, 20_000);
        int threads = 16;
        int scoresPerThread = 500;
        int users = 10;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startSignal = new CountDownLatch(1);
        AtomicInteger errors = new AtomicInteger();
        for (int t = 0; t < threads; t++) {
            final int threadIndex = t;
            pool.submit(() -> {
                try {
                    startSignal.await();
                    for (int i = 1; i <= scoresPerThread; i++) {
                        service.submitScore(GAME, "user-" + (i % users), threadIndex * scoresPerThread + i);
                        service.listPlayersAround(GAME, daily, "user-" + (i % users), 3);
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                }
            });
        }
        startSignal.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS));
        assertEquals(0, errors.get());

        assertBoardIsConsistent(service.getLeaderboard(daily), users, threads * scoresPerThread);
        assertBoardIsConsistent(service.getLeaderboard(weekly), users, threads * scoresPerThread);
    }

    private static void assertBoardIsConsistent(List<RankedEntry> board, int expectedUsers, int expectedTopScore) {
        assertEquals(expectedUsers, board.size(), "each user must appear exactly once");
        assertEquals(expectedUsers, board.stream().map(RankedEntry::getUserId).distinct().count());
        assertEquals(expectedTopScore, board.get(0).getScore());
        for (int i = 1; i < board.size(); i++) {
            assertTrue(board.get(i - 1).getScore() >= board.get(i).getScore(), "ranking must be sorted");
            assertEquals(i + 1, board.get(i).getRank(), "ranks must be contiguous");
        }
    }
}
