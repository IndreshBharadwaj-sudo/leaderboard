package leaderboard;

import leaderboard.repository.InMemoryGameRepository;
import leaderboard.repository.InMemoryLeaderboardRepository;
import leaderboard.service.DefaultLeaderboardService;
import leaderboard.service.LeaderboardService;
import leaderboard.service.UuidIdGenerator;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/** Driver program, walks through the leaderboard service end to end. */

/*
   Assumptions
1. Users and games are opaque string ids; user management is out of scope.
2. A campaign window is `[start, end)` in epoch seconds.
3. Score is an int in `[0, 1_000_000_000]`, as given in the problem.
4. On equal scores the user who got there first ranks higher.
5. Ranks are dense positions 1..n, which keeps neighbour windows unambiguous.
6. Campaigns are created explicitly; a scheduler that creates tomorrow's daily board would just call
   `createLeaderboard`.
7. Everything lives in the memory of one instance.
8. Editing or deleting a campaign, and the reward payout itself, are out of scope; `getWinner`
   provides the input for payout.
 */
public final class Demo {

    private static final String GAME = "bubble-shooter";

    private Demo() {
    }

    public static void main(String[] args) {
        LeaderboardService service = new DefaultLeaderboardService(
                new InMemoryGameRepository(),
                new InMemoryLeaderboardRepository(),
                Clock.systemUTC(),
                new UuidIdGenerator());

        service.registerGame(GAME, "Bubble Shooter");
        service.registerGame("carrom", "Carrom");
        System.out.println("Games            : " + service.getSupportedGames());

        long now = Instant.now().getEpochSecond();
        String daily = service.createLeaderboard(GAME, now - 60, now + 86_400);
        String weekly = service.createLeaderboard(GAME, now - 60, now + 7 * 86_400);
        String notStarted = service.createLeaderboard(GAME, now + 3_600, now + 7_200);
        System.out.println("Active campaigns : " + service.getActiveLeaderboards(GAME).size() + " of "
                + service.getLeaderboardsOfGame(GAME).size());

        service.submitScore(GAME, "alice", 500);
        service.submitScore(GAME, "bob", 700);
        service.submitScore(GAME, "carol", 650);
        service.submitScore(GAME, "dave", 300);
        service.submitScore(GAME, "erin", 300);
        // alice already has 500, so the 400 is dropped and the 900 replaces it on both campaigns.
        System.out.println("alice 400 updated: " + service.submitScore(GAME, "alice", 400).size() + " boards");
        System.out.println("alice 900 updated: " + service.submitScore(GAME, "alice", 900).size() + " boards");

        System.out.println("Daily            : " + service.getLeaderboard(daily));
        System.out.println("Weekly           : " + service.getLeaderboard(weekly));
        System.out.println("Not started yet  : " + service.getLeaderboard(notStarted));
        System.out.println("Top 2            : " + service.getTopScorers(daily, 2));
        System.out.println("Rank of carol    : " + service.getRank(daily, "carol").orElseThrow());
        System.out.println("Around carol     : " + service.listPlayersAround(GAME, daily, "carol", 1));
        System.out.println("Next 2 of bob    : " + service.listPlayersNext(GAME, daily, "bob", 2));
        System.out.println("Prev 2 of erin   : " + service.listPlayersPrev(GAME, daily, "erin", 2));

        try {
            service.submitScore(GAME, "alice", -5);
        } catch (RuntimeException e) {
            System.out.println("Invalid score    : " + e.getMessage());
        }
        try {
            service.getWinner(daily);
        } catch (RuntimeException e) {
            System.out.println("Winner too early : " + e.getMessage());
        }

        CompletableFuture<String> cf = new CompletableFuture<>();
    }
}