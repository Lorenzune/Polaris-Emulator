package com.eu.habbo.habbohotel.mentions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class MentionSpamGuardTest {
    @Test
    void permitsOnlyOneConcurrentRequestWithinTheWindow() {
        ConcurrentHashMap<Integer, Long> bucket = new ConcurrentHashMap<>();
        long accepted = IntStream.range(0, 100)
                .parallel()
                .filter(i -> MentionManager.acquire(bucket, 7, 1000, 3000))
                .count();
        assertEquals(1, accepted);
        assertFalse(MentionManager.acquire(bucket, 7, 3999, 3000));
        assertTrue(MentionManager.acquire(bucket, 7, 4000, 3000));
        assertTrue(MentionManager.acquire(bucket, 8, 4000, 3000));
    }
}
