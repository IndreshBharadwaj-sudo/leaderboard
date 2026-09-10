package leaderboard;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

/** Test clock whose time can be moved forward to simulate campaigns starting and ending. */
public class MutableClock extends Clock {

    // Volatile: the concurrency test advances time from one thread while workers read it.
    private volatile Instant instant;
    private final ZoneId zone;

    public MutableClock(Instant instant) {
        this(instant, ZoneId.of("UTC"));
    }

    private MutableClock(Instant instant, ZoneId zone) {
        this.instant = instant;
        this.zone = zone;
    }

    public void advanceSeconds(long seconds) {
        instant = instant.plusSeconds(seconds);
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId newZone) {
        return new MutableClock(instant, newZone);
    }

    @Override
    public Instant instant() {
        return instant;
    }
}