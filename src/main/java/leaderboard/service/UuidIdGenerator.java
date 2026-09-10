package leaderboard.service;

import java.util.UUID;

/** Default {@link IdGenerator}. */
public class UuidIdGenerator implements IdGenerator {

    @Override
    public String newId() {
        return UUID.randomUUID().toString();
    }
}